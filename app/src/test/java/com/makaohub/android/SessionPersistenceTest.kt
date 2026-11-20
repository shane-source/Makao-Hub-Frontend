package com.makaohub.android

import com.makaohub.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SessionPersistenceTest {
    private class FakeStore : SessionStore {
        var saved: AuthTokens? = null
        override fun read() = saved
        override fun write(tokens: AuthTokens) { saved = tokens }
        override fun clear() { saved = null }
    }
    private fun tokens(access:String,refresh:String) = """{"accessToken":"$access","refreshToken":"$refresh","role":"RENTER","userId":"user-1"}"""
    private val profile = """{"id":"user-1","fullName":"Test","email":"test@example.test","role":"RENTER","accountStatus":"ACTIVE"}"""
    private fun response(value:String, status:Int=200) = MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(value)

    @Test fun savedSessionRestoresAndPersistsRotatedTokenAndSignOutClearsIt() = runBlocking {
        val server=MockWebServer(); server.start()
        try {
            val store=FakeStore(); val first=AuthRepository(createMakaoApi(server.url("/").toString()),store)
            server.enqueue(response(tokens("access-1","refresh-1")))
            first.verify("challenge","123456",OtpFlow.LOGIN,SignupRole.RENTER)
            assertEquals("refresh-1",store.saved?.refreshToken)
            val restored=AuthRepository(createMakaoApi(server.url("/").toString()),store)
            assertTrue(restored.restore())
            server.enqueue(response("{}",401)); server.enqueue(response(tokens("access-2","refresh-2"))); server.enqueue(response(profile))
            assertEquals("RENTER",restored.profile().role)
            assertEquals("refresh-2",store.saved?.refreshToken)
            server.enqueue(response("",204)); restored.logout()
            assertNull(store.saved)
            assertFalse(AuthRepository(createMakaoApi(server.url("/").toString()),store).restore())
        } finally { server.shutdown() }
    }

    @Test fun inactivityCannotRestoreAnInMemorySession() = runBlocking {
        val store = FakeStore().apply { saved = AuthTokens("access", "refresh", "RENTER", "user-1") }
        val repository = AuthRepository(createMakaoApi("http://localhost:8080/"), store)
        assertTrue(repository.restore())
        repository.expireLocally()
        assertFalse(repository.restore())
        assertNull(repository.sessionRole)
        assertNull(store.saved)
    }

    @Test fun refreshFailureDoesNotLeaveAnOldRotatedTokenToReplay() = runBlocking {
        val server=MockWebServer(); server.start()
        try {
            val store=FakeStore(); store.saved=AuthTokens("expired","old-refresh","RENTER","user-1")
            val restored=AuthRepository(createMakaoApi(server.url("/").toString()),store)
            assertTrue(restored.restore())
            server.enqueue(response("{}",401)); server.enqueue(response("{}",503))
            try { restored.profile(); fail("Refresh failure must expire the session") } catch (_:SessionExpired) { }
            assertNull(store.saved)
        } finally { server.shutdown() }
    }
}
