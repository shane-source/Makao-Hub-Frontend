package com.makaohub.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** State checks only: no server calls or email delivery. */
class SplashNavigationTest {
    @Test fun splashCompletionOpensWelcomeWithoutGrantingASession() {
        val model = AuthViewModel()
        assertEquals(Page.SPLASH, model.page)
        model.finishSplash()
        assertEquals(Page.ENTRY, model.page)
        assertNull(model.profile)
        assertNull(model.challenge)
    }

    @Test fun repeatedTapAndLateTimerCannotReplaceTheNextScreen() {
        val model = AuthViewModel()
        model.finishSplash()
        model.finishSplash()
        assertEquals(Page.ENTRY, model.page)
        model.navigate(Page.WELCOME)
        model.finishSplash()
        assertEquals(Page.WELCOME, model.page)
    }
}
