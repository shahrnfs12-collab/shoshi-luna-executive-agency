package com.shoshiluna.executive.core.empire

import android.content.Context
import com.google.gson.Gson

/** Crash-safe local persistence for mission state. No secret material is stored here. */
internal class EmpireMissionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("empire_missions_v1", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): EmpireState = prefs.getString(KEY_STATE, null)
        ?.let { runCatching { gson.fromJson(it, EmpireState::class.java) }.getOrNull() }
        ?: EmpireState()

    fun save(state: EmpireState) {
        check(prefs.edit().putString(KEY_STATE, gson.toJson(state)).commit()) {
            "Mission state persistence failed"
        }
    }

    companion object { private const val KEY_STATE = "empire_state" }
}
