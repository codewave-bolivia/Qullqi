package com.codewave.qullqi.domain.session

import com.codewave.qullqi.domain.system.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Re-authentication policy (spec 003, FR-002a/FR-002b). Process-wide and in memory on purpose:
 * a new process (cold start or Android restoring the task) always starts locked.
 */
class AppLock(private val clock: Clock, private val timeoutMillis: Long = TIMEOUT_MILLIS) {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    /** Call only after a successful PIN or biometric check. */
    fun unlock() {
        backgroundedAt = null
        _locked.value = false
    }

    fun onBackground() {
        backgroundedAt = clock.nowMillis()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (clock.nowMillis() - since > timeoutMillis) _locked.value = true
    }

    companion object {
        const val TIMEOUT_MILLIS = 5 * 60_000L
    }
}
