package com.codewave.qullqi.feature

import com.codewave.qullqi.data.FakeClock
import com.codewave.qullqi.domain.session.AppLock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {
    private val clock = FakeClock()
    private val lock = AppLock(clock)

    @Test fun lockedAtProcessStart() = assertTrue(lock.locked.value)

    @Test fun unlockOpens() {
        lock.unlock()
        assertFalse(lock.locked.value)
    }

    @Test fun shortBackgroundKeepsUnlocked() {
        lock.unlock()
        lock.onBackground()
        clock.advance(4 * 60_000 + 59_000)
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test fun longBackgroundLocks() {
        lock.unlock()
        lock.onBackground()
        clock.advance(5 * 60_000 + 1_000)
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test fun foregroundWithoutBackgroundDoesNothing() {
        lock.unlock()
        clock.advance(60 * 60_000)
        lock.onForeground()
        assertFalse(lock.locked.value)
    }
}
