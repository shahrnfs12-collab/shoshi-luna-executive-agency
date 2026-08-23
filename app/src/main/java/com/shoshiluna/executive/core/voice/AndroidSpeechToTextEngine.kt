package com.shoshiluna.executive.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class SttState {
    data object Idle : SttState()
    data object Listening : SttState()
    data class Partial(val text: String) : SttState()
    data class Final(val text: String) : SttState()
    data class Error(val code: Int, val message: String) : SttState()
    data object PermissionRequired : SttState()
    data object ServiceUnavailable : SttState()
}

class AndroidSpeechToTextEngine(
    context: Context,
    private val preferredLocale: Locale = Locale.getDefault()
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    val state: StateFlow<SttState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var released = false

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(onFinalTranscript: (String) -> Unit) {
        if (released) return
        if (!hasPermission()) { _state.value = SttState.PermissionRequired; return }
        if (!isAvailable()) { _state.value = SttState.ServiceUnavailable; return }
        destroyRecognizerOnly()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (!released) _state.value = SttState.Listening }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) { if (!released) _state.value = SttState.Error(error, errorMessage(error)) }
                override fun onResults(results: Bundle?) {
                    if (released) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    _state.value = SttState.Final(text)
                    if (text.isNotBlank()) onFinalTranscript(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (released) return
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    if (text.isNotBlank()) _state.value = SttState.Partial(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLocale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { _state.value = SttState.Error(-1, "Failed to start recognizer: ${it.message}") }
    }

    fun stopListening() { if (!released) runCatching { recognizer?.stopListening() } }
    fun cancel() { if (!released) { runCatching { recognizer?.cancel() }; _state.value = SttState.Idle } }
    fun release() { if (released) return; released = true; destroyRecognizerOnly(); _state.value = SttState.Idle }

    private fun destroyRecognizerOnly() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognizer server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
        else -> "Speech recognition error $code"
    }
}
