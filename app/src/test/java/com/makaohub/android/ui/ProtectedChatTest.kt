package com.makaohub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedChatTest {
    @Test fun `ISO timestamp renders only hour and minute`() {
        assertEquals("09:36", protectedChatTime("2026-10-25T09:36:42+03:00"))
    }

    @Test fun `malformed timestamp does not break the conversation`() {
        assertEquals("", protectedChatTime("not-a-timestamp"))
    }
}
