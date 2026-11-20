package com.makaohub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedChatTest {
    @Test fun `timestamps use Kenya time and AM PM`() {
        assertEquals("9:36 am", protectedChatTime("2026-10-25T09:36:42+03:00").lowercase())
        assertEquals("12:30 am", protectedChatTime("2026-10-25T21:30:00Z").lowercase())
    }

    @Test fun `malformed timestamp does not break the conversation`() {
        assertEquals("Time unavailable", protectedChatTime("not-a-timestamp"))
    }
}
