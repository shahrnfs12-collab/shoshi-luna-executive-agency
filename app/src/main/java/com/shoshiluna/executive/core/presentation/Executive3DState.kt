package com.shoshiluna.executive.core.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-safe state machine for avatar animation intent.
 * Rendering stays isolated from business / treasury / execution logic.
 */
object Executive3DState {
    private val _shoshiAnimation = MutableStateFlow(ExecutiveAnimation.IDLE)
    val shoshiAnimation = _shoshiAnimation.asStateFlow()

    private val _lunaAnimation = MutableStateFlow(ExecutiveAnimation.IDLE)
    val lunaAnimation = _lunaAnimation.asStateFlow()

    fun setAnimation(avatar: ExecutiveAvatar, animation: ExecutiveAnimation) {
        when (avatar) {
            ExecutiveAvatar.SHOSHI -> _shoshiAnimation.value = animation
            ExecutiveAvatar.LUNA -> _lunaAnimation.value = animation
        }
    }

    fun resetAll() {
        _shoshiAnimation.value = ExecutiveAnimation.IDLE
        _lunaAnimation.value = ExecutiveAnimation.IDLE
    }
}
