package com.makaohub.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

/** State checks only: no server calls or email delivery. */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashNavigationTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
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
