package com.makaohub.android.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

/** Public signup roles only. The authenticated role always comes from the server. */
enum class SignupRole(val title: String, val description: String) {
    RENTER("Renter", "Find a whole home or a shared space."),
    LANDLORD("Landlord", "List whole properties or units and review applications."),
    SUBLESSOR("Sublessor", "List a spare room or servant quarter and find roommates.")
}

// Credential-bearing models deliberately have no generated toString().
class RegisterRequest(val fullName: String, val email: String, val phoneNumber: String,
    val password: String, val role: SignupRole, val termsAndPrivacyAccepted: Boolean)
class LoginRequest(val email: String, val password: String, val expectedRole: SignupRole)
class VerifyOtpRequest(val challengeId: String, val code: String)
class ForgotPasswordRequest(val email: String)
class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)
class RefreshRequest(val refreshToken: String)
class GoogleRequest(val idToken: String, val expectedRole: SignupRole)
class GoogleRegisterRequest(val idToken: String, val fullName: String,
    val role: SignupRole, val termsAndPrivacyAccepted: Boolean)
class AcceptedResponse(val message: String)
class ReportRequest(val subjectKind: String, val subjectId: String, val category: String, val description: String)
class ReportResponse(val reportId: String, val state: String)
class OtpChallenge(val challengeId: String, val purpose: String, val maskedEmail: String, val expiresAt: String)
class AuthTokens(val accessToken: String, val refreshToken: String, val role: String, val userId: String)
class MyProfile(val id: String, val fullName: String, val email: String, val role: String,
    val accountStatus: String, val verificationStatus: String?, val phoneNumber: String? = null)

/** Paths and JSON names match the Spring Boot AuthController and ProfileController. */
interface MakaoApi {
    @GET("api/v1/renter/verification") suspend fun renterVerification(@Header("Authorization") h:String):OwnerVerification
    @GET("api/v1/renter/verification/documents") suspend fun renterDocuments(@Header("Authorization") h:String):List<OwnerVerificationDocument>
    @POST("api/v1/renter/verification/documents/upload-url") suspend fun renterTicket(@Header("Authorization") h:String,@Body body:VerificationUploadRequest):VerificationUploadTicket
    @POST("api/v1/renter/verification/documents/{id}/confirm") suspend fun renterConfirm(@Header("Authorization") h:String,@Path("id") id:String):Response<Unit>
    @POST("api/v1/renter/verification/submit") suspend fun renterSubmit(@Header("Authorization") h:String):OwnerVerification
    @GET("api/v1/renter/homes") suspend fun homes(@Header("Authorization") h:String,@Query("page") page:Int,@Query("area") area:String?,@Query("minRentKes") min:Long?,@Query("maxRentKes") max:Long?,@Query("source") source:String?,@Query("minBedrooms") bedrooms:Int?,@Query("furnished") furnished:Boolean?,@Query("propertyType") propertyType:String?,@Query("availableBy") availableBy:String?,@Query("saved") saved:Boolean):HomePage
    @GET("api/v1/renter/homes/{source}/{id}") suspend fun home(@Header("Authorization") h:String,@Path("source") source:String,@Path("id") id:String):RenterHome
    @GET("api/v1/renter/homes/{source}/{id}/photos") suspend fun homePhotos(@Header("Authorization") h:String,@Path("source") source:String,@Path("id") id:String):List<HomeMedia>
    @PUT("api/v1/renter/homes/{source}/{id}/save") suspend fun saveHome(@Header("Authorization") h:String,@Path("source") source:String,@Path("id") id:String):Response<Unit>
    @DELETE("api/v1/renter/homes/{source}/{id}/save") suspend fun removeHome(@Header("Authorization") h:String,@Path("source") source:String,@Path("id") id:String):Response<Unit>
    @GET("api/v1/payments/prices/{type}") suspend fun unlockPrice(@Header("Authorization") h:String,@Path("type") type:String):UnlockPrice
    @POST("api/v1/payments/status") suspend fun paymentStatus(@Header("Authorization") h:String,@Body body:PaymentStatusBody):OwnerPayment
    @POST("api/v1/payments/paystack/reconcile") suspend fun reconcilePaystack(@Header("Authorization") h:String,@Body body:PaystackReconcileBody):OwnerPayment
    @GET("api/v1/renter/listings/{id}/location/exact") suspend fun unlockedLocation(@Header("Authorization") h:String,@Path("id") id:String):OwnerLocation
    @GET("api/v1/renter/listings/{id}/phone") suspend fun unlockedPhone(@Header("Authorization") h:String,@Path("id") id:String):UnlockedPhone
    @GET("api/v1/renter/listings/{id}/media/video") suspend fun unlockedVideos(@Header("Authorization") h:String,@Path("id") id:String):List<HomeMedia>
    @GET("api/v1/renter/applications") suspend fun renterApplications(@Header("Authorization") h:String):List<OwnerApplication>
    @GET("api/v1/renter/sublessor-applications") suspend fun renterSharedApplications(@Header("Authorization") h:String):List<OwnerApplication>
    @POST("api/v1/renter/{kind}/listing/{id}") suspend fun renterApply(@Header("Authorization") h:String,@Path("kind") kind:String,@Path("id") id:String):OwnerApplication
    @DELETE("api/v1/renter/{kind}/{id}") suspend fun renterWithdraw(@Header("Authorization") h:String,@Path("kind") kind:String,@Path("id") id:String):OwnerApplication
    @POST("api/v1/renter/bookings/{kind}/{id}") suspend fun renterBook(@Header("Authorization") h:String,@Path("kind") kind:String,@Path("id") id:String):RenterBooking
    @GET("api/v1/renter/bookings/{id}") suspend fun renterBooking(@Header("Authorization") h:String,@Path("id") id:String):RenterBooking
    @POST("api/v1/renter/bookings/{id}/move-in/confirm") suspend fun renterMoveIn(@Header("Authorization") h:String,@Path("id") id:String):RenterBooking
    @POST("api/v1/renter/bookings/{id}/agreement/confirm") suspend fun renterAgreement(@Header("Authorization") h:String,@Path("id") id:String):RenterBooking
    @GET("api/v1/renter/rental-chat/threads") suspend fun renterThreads(@Header("Authorization") h:String,@Query("page") page:Int,@Query("size") size:Int=20):OwnerPage<OwnerThread>
    @GET("api/v1/renter/rental-chat/threads/{id}/messages") suspend fun renterMessages(@Header("Authorization") h:String,@Path("id") id:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=50):OwnerMessagePage
    @GET("api/v1/renter/roommate-chat/threads") suspend fun renterRoomThreads(@Header("Authorization") h:String,@Query("page") page:Int,@Query("size") size:Int=20):SharedThreadPage
    @GET("api/v1/renter/roommate-chat/threads/{id}/messages") suspend fun renterRoomMessages(@Header("Authorization") h:String,@Path("id") id:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=50):SharedMessagePage
    @POST("api/v1/reports") suspend fun report(@Header("Authorization") h:String,@Body body:ReportRequest):ReportResponse
    @POST("api/v1/auth/register") suspend fun register(@Body body: RegisterRequest): OtpChallenge
    @POST("api/v1/auth/register/verify-otp") suspend fun verifyRegistrationOtp(@Body body: VerifyOtpRequest): AuthTokens
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginRequest): OtpChallenge
    @POST("api/v1/auth/login/verify-otp") suspend fun verifyOtp(@Body body: VerifyOtpRequest): AuthTokens
    @POST("api/v1/auth/google") suspend fun google(@Body body: GoogleRequest): OtpChallenge
    @POST("api/v1/auth/google/register") suspend fun googleRegister(@Body body: GoogleRegisterRequest): OtpChallenge
    @POST("api/v1/auth/google/verify-otp") suspend fun verifyGoogleOtp(@Body body: VerifyOtpRequest): AuthTokens
    @POST("api/v1/auth/forgot-password") suspend fun forgotPassword(@Body body: ForgotPasswordRequest): AcceptedResponse
    @POST("api/v1/auth/forgot-password/reset") suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<Unit>
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body body: RefreshRequest): AuthTokens
    @POST("api/v1/auth/logout") suspend fun logout(@Body body: RefreshRequest): Response<Unit>
    @GET("api/v1/profile/me") suspend fun profile(@Header("Authorization") authorization: String): MyProfile

    @GET("api/v1/landlord/listings/dashboard") suspend fun landlordDashboard(@Header("Authorization") auth: String): LandlordSummary
    @GET("api/v1/landlord/verification") suspend fun landlordVerification(@Header("Authorization") auth: String): OwnerVerification
    @GET("api/v1/landlord/verification/documents") suspend fun landlordVerificationDocuments(@Header("Authorization") auth: String): List<OwnerVerificationDocument>
    @POST("api/v1/landlord/verification/documents/upload-url") suspend fun landlordVerificationUpload(@Header("Authorization") auth: String, @Body body: VerificationUploadRequest): VerificationUploadTicket
    @POST("api/v1/landlord/verification/documents/{id}/confirm") suspend fun confirmLandlordVerificationDocument(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @POST("api/v1/landlord/verification/submit") suspend fun submitLandlordVerification(@Header("Authorization") auth: String): OwnerVerification
    @GET("api/v1/landlord/listings") suspend fun landlordListings(@Header("Authorization") auth: String): List<OwnerListing>
    @POST("api/v1/landlord/listings/drafts") suspend fun createListing(@Header("Authorization") auth: String, @Body body: ListingDraft): OwnerListing
    @PATCH("api/v1/landlord/listings/{id}") suspend fun updateListing(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: ListingDraft): OwnerListing
    @DELETE("api/v1/landlord/listings/{id}") suspend fun deleteListingDraft(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @POST("api/v1/landlord/listings/{id}/publish") suspend fun publishListing(@Header("Authorization") auth: String, @Path("id") id: String): OwnerListing
    @GET("api/v1/landlord/listings/{id}/review") suspend fun landlordListingReview(@Header("Authorization") auth: String, @Path("id") id: String): OwnerListingReview
    @POST("api/v1/landlord/listings/{id}/submit-review") suspend fun submitLandlordListingReview(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @POST("api/v1/landlord/listings/{id}/revise-review") suspend fun reviseLandlordListingReview(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @POST("api/v1/landlord/listings/{id}/pause") suspend fun pauseListing(@Header("Authorization") auth: String, @Path("id") id: String): OwnerListing
    @POST("api/v1/landlord/listings/{id}/resume") suspend fun resumeListing(@Header("Authorization") auth: String, @Path("id") id: String): OwnerListing
    @GET("api/v1/landlord/listings/{id}/location") suspend fun ownerLocation(@Header("Authorization") auth: String, @Path("id") id: String): OwnerLocation
    @PUT("api/v1/landlord/listings/{id}/location") suspend fun saveLocation(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: OwnerLocation): OwnerLocation
    @GET("api/v1/landlord/listings/{id}/media") suspend fun ownerMedia(@Header("Authorization") auth: String, @Path("id") id: String): List<OwnerMedia>
    @POST("api/v1/landlord/listings/{id}/media/upload-url") suspend fun mediaUpload(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: MediaUploadRequest): MediaUploadTicket
    @POST("api/v1/landlord/listings/{id}/media/{media}/confirm") suspend fun confirmMedia(@Header("Authorization") auth: String, @Path("id") id: String, @Path("media") media: String): OwnerMedia
    @DELETE("api/v1/landlord/listings/{id}/media/{media}") suspend fun deleteMedia(@Header("Authorization") auth: String, @Path("id") id: String, @Path("media") media: String): Response<Unit>
    @GET("api/v1/landlord/applications") suspend fun landlordApplications(@Header("Authorization") auth: String): List<OwnerApplication>
    @POST("api/v1/landlord/applications/{id}/review") suspend fun reviewApplication(@Header("Authorization") auth: String, @Path("id") id: String): OwnerApplication
    @POST("api/v1/landlord/applications/{id}/approve") suspend fun approveApplication(@Header("Authorization") auth: String, @Path("id") id: String): OwnerApplication
    @POST("api/v1/landlord/applications/{id}/reject") suspend fun rejectApplication(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: OwnerApplicationRejection): OwnerApplication
    @GET("api/v1/landlord/rental-chat/threads") suspend fun landlordThreads(@Header("Authorization") auth: String, @Query("page") page: Int, @Query("size") size: Int = 20): OwnerPage<OwnerThread>
    @GET("api/v1/landlord/rental-chat/threads/{id}/messages") suspend fun landlordMessages(@Header("Authorization") auth: String, @Path("id") id: String, @Query("cursor") cursor: String?, @Query("limit") limit: Int = 50): OwnerMessagePage
    @GET("api/v1/payments") suspend fun ownerPayments(@Header("Authorization") auth: String, @Query("page") page: Int, @Query("size") size: Int = 20): OwnerPage<OwnerPayment>
    @POST("api/v1/payments/intents") suspend fun ownerCheckout(@Header("Authorization") auth: String, @Header("Idempotency-Key") key: String, @Body body: OwnerCheckout): OwnerPayment
    @POST("api/v1/payments/mock/completions") suspend fun completeOwnerMock(@Header("Authorization") auth: String, @Body body: MockCompletion): OwnerPayment

    @GET("api/v1/sublessor/verification") suspend fun sublessorVerification(@Header("Authorization") auth: String): OwnerVerification
    @GET("api/v1/sublessor/verification/documents") suspend fun sublessorDocuments(@Header("Authorization") auth: String): List<OwnerVerificationDocument>
    @POST("api/v1/sublessor/verification/documents/upload-url") suspend fun sublessorEvidenceTicket(@Header("Authorization") auth: String, @Body body: VerificationUploadRequest): VerificationUploadTicket
    @POST("api/v1/sublessor/verification/documents/{id}/confirm") suspend fun confirmSublessorEvidence(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @POST("api/v1/sublessor/verification/submit") suspend fun submitSublessorVerification(@Header("Authorization") auth: String): OwnerVerification
    @GET("api/v1/sublessor/listings") suspend fun sublessorListings(@Header("Authorization") auth: String): List<SharedListing>
    @POST("api/v1/sublessor/listings") suspend fun createSharedListing(@Header("Authorization") auth: String, @Body body: SharedListingRequest): SharedListing
    @PUT("api/v1/sublessor/listings/{id}") suspend fun updateSharedListing(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: SharedListingRequest): SharedListing
    @POST("api/v1/sublessor/listings/{id}/{action}") suspend fun sharedListingAction(@Header("Authorization") auth: String, @Path("id") id: String, @Path("action") action: String): SharedListing
    @GET("api/v1/sublessor/listings/{id}/review") suspend fun sublessorListingReview(@Header("Authorization") auth: String, @Path("id") id: String): OwnerListingReview
    @POST("api/v1/sublessor/listings/{id}/submit-review") suspend fun submitSublessorListingReview(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @POST("api/v1/sublessor/listings/{id}/revise-review") suspend fun reviseSublessorListingReview(@Header("Authorization") auth: String, @Path("id") id: String): Response<Unit>
    @GET("api/v1/sublessor/listings/{id}/location") suspend fun sharedLocation(@Header("Authorization") auth: String, @Path("id") id: String): OwnerLocation
    @GET("api/v1/sublessor/listings/{id}/household") suspend fun sharedHousehold(@Header("Authorization") auth: String, @Path("id") id: String): SharedHousehold
    @PUT("api/v1/sublessor/listings/{id}/household") suspend fun saveSharedHousehold(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: SharedHousehold): SharedHousehold
    @PUT("api/v1/sublessor/listings/{id}/location") suspend fun saveSharedLocation(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: OwnerLocation): OwnerLocation
    @GET("api/v1/sublessor/listings/{id}/media") suspend fun sharedMedia(@Header("Authorization") auth: String, @Path("id") id: String): List<OwnerMedia>
    @POST("api/v1/sublessor/listings/{id}/media/upload-url") suspend fun sharedMediaTicket(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: MediaUploadRequest): MediaUploadTicket
    @POST("api/v1/sublessor/listings/{id}/media/{media}/confirm") suspend fun confirmSharedMedia(@Header("Authorization") auth: String, @Path("id") id: String, @Path("media") media: String): OwnerMedia
    @DELETE("api/v1/sublessor/listings/{id}/media/{media}") suspend fun deleteSharedMedia(@Header("Authorization") auth: String, @Path("id") id: String, @Path("media") media: String): Response<Unit>
    @GET("api/v1/sublessor/applications") suspend fun sharedApplications(@Header("Authorization") auth: String): List<OwnerApplication>
    @POST("api/v1/sublessor/applications/{id}/{action}") suspend fun sharedApplicationAction(@Header("Authorization") auth: String, @Path("id") id: String, @Path("action") action: String): OwnerApplication
    @POST("api/v1/sublessor/applications/{id}/reject") suspend fun rejectSharedApplication(@Header("Authorization") auth: String, @Path("id") id: String, @Body body: SharedRejection): OwnerApplication
    @GET("api/v1/sublessor/roommate-interests") suspend fun roommateInterests(@Header("Authorization") auth: String): List<SharedInterest>
    @POST("api/v1/sublessor/roommate-interests/{id}/{action}") suspend fun roommateInterestAction(@Header("Authorization") auth: String, @Path("id") id: String, @Path("action") action: String): SharedInterest
    @GET("api/v1/sublessor/roommate-chat/threads") suspend fun roommateThreads(@Header("Authorization") auth: String, @Query("page") page: Int, @Query("size") size: Int = 20): SharedThreadPage
    @GET("api/v1/sublessor/roommate-chat/threads/{id}/messages") suspend fun roommateMessages(@Header("Authorization") auth: String, @Path("id") id: String, @Query("cursor") cursor: String?, @Query("limit") limit: Int = 50): SharedMessagePage
    @GET("api/v1/sublessor/roommate-preferences") suspend fun sharedPreferences(@Header("Authorization") auth: String): SharedPreferences
    @PUT("api/v1/sublessor/roommate-preferences") suspend fun saveSharedPreferences(@Header("Authorization") auth: String, @Body body: SharedPreferences): SharedPreferences
    @GET("api/v1/sublessor/roommate-discovery") suspend fun sharedDiscovery(@Header("Authorization") auth: String, @Query("page") page: Int, @Query("size") size: Int = 20): SharedDiscoveryPage
    @GET("api/v1/sublessor/roommate-discovery/{id}") suspend fun sharedRenterProfile(@Header("Authorization") auth: String, @Path("id") id: String): SharedPublicProfile
}
