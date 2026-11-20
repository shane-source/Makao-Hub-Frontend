package com.makaohub.android

import com.makaohub.android.data.sessionInactive
import org.junit.Assert.*
import org.junit.Test

class SessionInactivityTest {
    @Test fun expiresAfterOneMinuteWhileShorterGapsRemainSignedIn() {
        assertFalse(sessionInactive(100_000, 10_000, 159_999, 69_999))
        assertTrue(sessionInactive(100_000, 10_000, 160_000, 70_000))
        assertTrue(sessionInactive(100_000, 10_000, 100_001, 70_000))
    }
    @Test fun elapsedClockResetStillUsesWallTimeAndClockRollbackFailsClosed() {
        assertFalse(sessionInactive(100_000, 70_000, 120_000, 5_000))
        assertTrue(sessionInactive(100_000, 70_000, 160_000, 5_000))
        assertTrue(sessionInactive(100_000, 70_000, 99_999, 70_001))
    }
}
