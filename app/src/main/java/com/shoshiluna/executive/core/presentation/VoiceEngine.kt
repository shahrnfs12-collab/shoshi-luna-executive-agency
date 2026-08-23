package com.shoshiluna.executive.core.presentation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING, ERROR }

data class SpeechFragment(val text: String, val visemes: List<Int> = emptyList(), val durationMs: Long)

data class FixedVoiceProfile(
    val agentId: String,
    val localeTag: String,
    val pitch: Float,
    val speechRate: Float,
    val selectedVoiceName: String?
)

/**
 * Production voice policy: exactly two fixed Hebrew voice identities and no user-selectable catalog.
 * Android's Voice API does not expose reliable gender metadata, so selection is deterministic from
 * he-IL/Hebrew voices and each agent also has a fixed pitch/rate profile to keep the identities distinct.
 */
object VoiceEngine : TextToSpeech.OnInitListener {
    private val HEBREW_LOCALE: Locale = Locale.forLanguageTag("he-IL")

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState = _voiceState.asStateFlow()

    private val _profiles = MutableStateFlow(
        mapOf(
            "SHOSHI" to FixedVoiceProfile("SHOSHI", "he-IL", 1.08f, 0.96f, null),
            "LUNA" to FixedVoiceProfile("LUNA", "he-IL", 1.16f, 0.92f, null)
        )
    )
    val profiles = _profiles.asStateFlow()

    private var tts: TextToSpeech? = null

    fun initialize(context: Context) {
        shutdownInternal()
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _voiceState.value = VoiceState.ERROR
            return
        }
        val engine = tts ?: run {
            _voiceState.value = VoiceState.ERROR
            return
        }

        val languageResult = engine.setLanguage(HEBREW_LOCALE)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            _voiceState.value = VoiceState.ERROR
            return
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _voiceState.value = VoiceState.SPEAKING }
            override fun onDone(utteranceId: String?) { _voiceState.value = VoiceState.IDLE }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _voiceState.value = VoiceState.ERROR }
        })

        val hebrewVoices = engine.voices.orEmpty()
            .filter { v -> v.locale.language.equals("he", ignoreCase = true) }
            .sortedWith(
                compareBy<Voice>(
                    { !it.locale.toLanguageTag().equals("he-IL", ignoreCase = true) },
                    { it.isNetworkConnectionRequired },
                    { it.name }
                )
            )

        val shoshiVoice = hebrewVoices.firstOrNull()
        val lunaVoice = hebrewVoices.firstOrNull { it.name != shoshiVoice?.name } ?: shoshiVoice

        _profiles.value = mapOf(
            "SHOSHI" to FixedVoiceProfile(
                agentId = "SHOSHI",
                localeTag = "he-IL",
                pitch = 1.08f,
                speechRate = 0.96f,
                selectedVoiceName = shoshiVoice?.name
            ),
            "LUNA" to FixedVoiceProfile(
                agentId = "LUNA",
                localeTag = "he-IL",
                pitch = if (lunaVoice?.name == shoshiVoice?.name) 1.18f else 1.12f,
                speechRate = 0.92f,
                selectedVoiceName = lunaVoice?.name
            )
        )
        _voiceState.value = VoiceState.IDLE
    }

    fun profile(agentId: String): FixedVoiceProfile =
        _profiles.value[agentId.uppercase()] ?: _profiles.value.getValue("SHOSHI")

    fun speak(text: String, agentId: String) {
        if (text.isBlank()) return
        val engine = tts ?: run {
            _voiceState.value = VoiceState.ERROR
            return
        }

        val profile = profile(agentId)
        engine.language = HEBREW_LOCALE
        profile.selectedVoiceName?.let { voiceName ->
            engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
        }
        engine.setPitch(profile.pitch)
        engine.setSpeechRate(profile.speechRate)

        _voiceState.value = VoiceState.SPEAKING
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        if (result == TextToSpeech.ERROR) {
            _voiceState.value = VoiceState.ERROR
            return
        }
        AuditTrail.append(
            ExecutiveAudit(
                agentId = agentId.uppercase(),
                action = "SPEAK",
                result = "FIXED_HEBREW_TTS_REQUEST_ACCEPTED"
            )
        )
    }

    fun stopSpeech() {
        tts?.stop()
        _voiceState.value = VoiceState.IDLE
    }

    fun onUserSpeechDetected() { _voiceState.value = VoiceState.LISTENING }

    fun processSpeechToIntent(rawText: String, agentId: String): String {
        _voiceState.value = VoiceState.PROCESSING
        val intent = if (rawText.contains("spend", ignoreCase = true)) "TREASURY_SPEND" else "GENERAL_QUERY"
        AuditTrail.append(
            ExecutiveAudit(
                agentId = agentId.uppercase(),
                action = "VOICE_INTENT_RECOGNITION",
                result = "RECOGNIZED: $intent"
            )
        )
        return intent
    }

    fun shutdown() {
        shutdownInternal()
        _voiceState.value = VoiceState.IDLE
    }

    private fun shutdownInternal() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }
}
