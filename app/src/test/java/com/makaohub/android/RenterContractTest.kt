package com.makaohub.android
import com.makaohub.android.data.*
import com.makaohub.android.ui.allowedHomeMedia
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class RenterContractTest {
    private lateinit var server:MockWebServer
    private lateinit var auth:AuthRepository
    private lateinit var repo:RenterRepository
    private fun respond(body:String,code:Int=200) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(body)) }
    private suspend fun login(role:String="RENTER") {
        respond("""{"accessToken":"synthetic","refreshToken":"synthetic","role":"$role","userId":"renter"}""")
        auth.verify("challenge","123456",OtpFlow.LOGIN);server.takeRequest()
    }
    private suspend fun idle(model:RenterViewModel) { withTimeout(5000) { while(model.busy) delay(10) } }
    private val homeJson="""{"id":"home","source":"SUBLESSOR","title":"Synthetic shared home","description":"Synthetic","propertyType":"SPARE_ROOM","monthlyRentKes":18000,"bathroomCount":1,"furnished":true,"areaLabel":"Nairobi","approximateLatitude":-1.29,"approximateLongitude":36.82,"approximateRadiusMeters":500}"""
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher());server=MockWebServer();server.start();auth=AuthRepository(createMakaoApi(server.url("/").toString()));repo=RenterRepository(auth) }
    @After fun close() { server.shutdown();Dispatchers.resetMain() }
    @Test fun wrongRoleCannotAccessRenterRecords()=runBlocking {
        login("LANDLORD")
        try { repo.verification();fail("Wrong role") } catch(_:WorkspaceUnavailable) {}
        assertEquals(1,server.requestCount)
    }
    @Test fun evidenceIsOnlyBothIdSidesAndSelfieWithServerChecks()=runBlocking {
        login()
        assertEquals(setOf("ID_FRONT","ID_BACK","SELFIE"),RenterEvidence.entries.map { it.name }.toSet())
        val ready=RenterEvidence.entries.map { OwnerVerificationDocument(it.name,it.name,"READY","image/jpeg",100,true) }
        assertTrue(renterEvidenceComplete(ready));assertFalse(renterEvidenceComplete(ready.dropLast(1)))
        assertFalse(renterEvidenceComplete(ready.map { OwnerVerificationDocument(it.documentId,it.documentType,"READY","image/jpeg") }))
        respond("""{"documentId":"front","documentType":"ID_FRONT","contentType":"image/jpeg","uploadUrl":"http://invalid.test"}""")
        repo.ticket(RenterEvidence.ID_FRONT,true)
        val request=server.takeRequest()
        assertEquals("/api/v1/renter/verification/documents/upload-url",request.path)
        val body=JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertTrue(body["replaceExisting"].asBoolean);assertFalse(body.has("renterId"))
        assertTrue(isEvidenceConfirmation("/api/v1/renter/verification/documents/front/confirm"))
    }
    @Test fun realMixedHomeFilterUsesServerPaginationAndSource()=runBlocking {
        login();respond("""{"items":[$homeJson],"page":1,"hasNext":true}""")
        val page=repo.homes(HomeFilter(area="Nairobi",min="10000",max="22000",source="SUBLESSOR"),1)
        assertEquals("SUBLESSOR",page.items.single().source);assertTrue(page.hasNext)
        val path=server.takeRequest().path!!
        assertTrue(path.contains("page=1"));assertTrue(path.contains("source=SUBLESSOR"));assertFalse(path.contains("renterId"))
        assertNotNull(HomeFilter(min="25000",max="20000").error())
        assertNotNull(HomeFilter(min="1.2").error());assertNotNull(HomeFilter(max="9223372036854775808").error())
    }
    @Test fun failedEvidenceCanBeReselectedAndOldReadinessCannotRemain()=runBlocking {
        login();respond("""{"status":"NOT_STARTED"}""");respond("[]")
        val m=RenterViewModel(repo) { fail("Session expired") }
        try {
            m.start();idle(m);server.takeRequest();server.takeRequest()
            val first=byteArrayOf(1,2);val second=byteArrayOf(3,4)
            for((id,bytes) in listOf("first" to first,"second" to second)) {
                respond("""{"documentId":"$id","documentType":"ID_FRONT","contentType":"image/jpeg","uploadUrl":"http://invalid.test/not-storage"}""")
                try { m.beginEvidence(RenterEvidence.ID_FRONT,bytes);fail("Unsafe URL") } catch(_:IllegalArgumentException) {}
                assertEquals(id,m.pendingEvidence);server.takeRequest()
            }
            assertArrayEquals(byteArrayOf(0,0),first);assertFalse(renterEvidenceComplete(m.documents))
            m.clear();assertArrayEquals(byteArrayOf(0,0),second)
        } finally { m.clear() }
    }
    @Test fun entitlementDenialDoesNotRevealLocationAndCheckoutRetryKeepsSameKey()=runBlocking {
        login()
        val m=RenterViewModel(repo) { fail("Session expired") }
        try {
            respond(homeJson);respond("[]")
            m.open(Gson().fromJson(homeJson,RenterHome::class.java));idle(m);server.takeRequest();server.takeRequest()
            respond("{}",403);respond("""{"paymentType":"EXACT_LOCATION_UNLOCK","amountMinor":25000,"currency":"KES"}""")
            m.openAccess("EXACT_LOCATION_UNLOCK");idle(m)
            assertNull(m.location);assertEquals(RenterScreen.CHECKOUT,m.screen)
            assertEquals("/api/v1/renter/listings/home/location/exact",server.takeRequest().path)
            assertEquals("/api/v1/payments/prices/EXACT_LOCATION_UNLOCK",server.takeRequest().path)
            respond("{}",503);m.createPayment();idle(m);val first=server.takeRequest()
            respond("""{"id":"intent","paymentType":"EXACT_LOCATION_UNLOCK","targetKind":"SUBLESSOR_LISTING","listingId":"home","amountMinor":25000,"currency":"KES","status":"PENDING","providerReference":"synthetic","createdAt":"2026-10-15T10:00:00Z"}""")
            m.createPayment();idle(m);val retry=server.takeRequest()
            assertEquals(first.getHeader("Idempotency-Key"),retry.getHeader("Idempotency-Key"))
            val body=JsonParser.parseString(retry.body.readUtf8()).asJsonObject
            assertFalse(body.has("amountMinor"));assertFalse(body.has("renterId"));assertEquals("home",body["listingId"].asString)
            assertNull(m.location)
        } finally { m.clear() }
    }
    @Test fun protectedContactClearsOnBackgroundAndMediaHostsAreRestricted()=runBlocking {
        login();val m=RenterViewModel(repo) { fail("Session expired") }
        try {
            respond(homeJson);respond("[]");m.open(Gson().fromJson(homeJson,RenterHome::class.java));idle(m);server.takeRequest();server.takeRequest()
            respond("""{"ownerName":"Synthetic Owner","phoneNumber":"+254700000000"}""")
            m.openAccess("PHONE_UNLOCK");idle(m);assertNotNull(m.phone);assertEquals(RenterScreen.ACCESS,m.screen)
            m.background();assertNull(m.phone);assertNull(m.location);assertTrue(m.videos.isEmpty())
        } finally { m.clear() }
        assertTrue(allowedHomeMedia("https://synthetic.supabase.co/storage/v1/object/sign/media/file"))
        assertFalse(allowedHomeMedia("http://synthetic.supabase.co/storage/v1/object/sign/media/file"))
        assertFalse(allowedHomeMedia("https://evil.example/storage/v1/object/sign/media/file"))
    }
}
