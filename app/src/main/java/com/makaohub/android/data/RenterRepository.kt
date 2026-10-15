package com.makaohub.android.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.HttpException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
data class RenterHome(val id:String,val source:String,val title:String,val description:String,val propertyType:String,
    val monthlyRentKes:Long,val bedroomCount:Int?,val bathroomCount:Int,val furnished:Boolean,val availableFrom:String?,
    val areaLabel:String,val approximateLatitude:BigDecimal,val approximateLongitude:BigDecimal,val approximateRadiusMeters:Int)
class HomePage(val items:List<RenterHome>,val page:Int,val hasNext:Boolean)
class HomeMedia(val id:String,val mediaType:String,val contentType:String,val sortOrder:Int,val signedReadUrl:String)
class UnlockPrice(val paymentType:String,val amountMinor:Long,val currency:String)
class PaymentStatusBody(val paymentIntentId:String)
class UnlockedPhone(val ownerName:String,val phoneNumber:String)
class RenterBooking(val id:String,val listingId:String,val listingKind:String,val sourceApplicationId:String,
    val rentAmountMinor:Long,val depositAmountMinor:Long,val commissionAmountMinor:Long,val totalAmountMinor:Long,
    val currency:String,val status:String,val moveInConfirmedAt:String?,val releaseEligibleAt:String?,val releasedAt:String?,
    val renterAgreedAt:String?,val ownerAgreedAt:String?)
enum class RenterEvidence(val title:String) { ID_FRONT("National ID front"),ID_BACK("National ID back"),SELFIE("Live photo") }
internal fun renterEvidenceReady(type:RenterEvidence,docs:List<OwnerVerificationDocument>) =
    docs.any { it.documentType==type.name && it.status=="READY" && it.securityChecked }
internal fun renterEvidenceComplete(docs:List<OwnerVerificationDocument>)=RenterEvidence.entries.all { renterEvidenceReady(it,docs) }
data class HomeFilter(val area:String="",val min:String="",val max:String="",val source:String?=null,val bedrooms:String="",val furnished:Boolean?=null) {
    fun error():String? = when {
        area.length>150 -> "Use a shorter area name."
        min.isNotBlank() && min.toLongOrNull()?.let { it>=0 }!=true -> "Enter a valid minimum rent."
        max.isNotBlank() && max.toLongOrNull()?.let { it>=0 }!=true -> "Enter a valid maximum rent."
        min.isNotBlank() && max.isNotBlank() && min.toLong()>max.toLong() -> "Maximum rent must not be below minimum."
        bedrooms.isNotBlank() && bedrooms.toIntOrNull() !in 0..20 -> "Use a bedroom count from 0 to 20."
        source!=null && source !in listOf("LANDLORD","SUBLESSOR") -> "Choose a listing source."
        else -> null
    }
}
data class RenterApplication(val source:String,val value:OwnerApplication)
data class RenterThread(val id:String,val source:String,val unlocked:Boolean=true)
class RenterRepository(private val auth:AuthRepository) {
    suspend fun verification()=auth.authorized("RENTER") { a,h->a.renterVerification(h) }
    suspend fun documents()=auth.authorized("RENTER") { a,h->a.renterDocuments(h) }
    suspend fun ticket(type:RenterEvidence,replace:Boolean)=auth.authorized("RENTER") { a,h->a.renterTicket(h,VerificationUploadRequest(type.name,"image/jpeg",replace)) }
    suspend fun confirm(id:String)=auth.authorized("RENTER") { a,h->a.renterConfirm(h,id).check() }
    suspend fun submit()=auth.authorized("RENTER") { a,h->a.renterSubmit(h) }
    suspend fun homes(f:HomeFilter,page:Int=0,saved:Boolean=false)=auth.authorized("RENTER") { a,h->
        require(f.error()==null); a.homes(h,page,f.area.trim().ifBlank { null },f.min.toLongOrNull(),f.max.toLongOrNull(),f.source,f.bedrooms.toIntOrNull(),f.furnished,saved) }
    suspend fun home(v:RenterHome)=auth.authorized("RENTER") { a,h->a.home(h,v.source,v.id) }
    suspend fun photos(v:RenterHome)=auth.authorized("RENTER") { a,h->a.homePhotos(h,v.source,v.id) }
    suspend fun save(v:RenterHome,remove:Boolean)=auth.authorized("RENTER") { a,h->if(remove) a.removeHome(h,v.source,v.id).check() else a.saveHome(h,v.source,v.id).check() }
    suspend fun price(type:String)=auth.authorized("RENTER") { a,h->require(type in UNLOCKS); a.unlockPrice(h,type) }
    suspend fun checkout(key:String,type:String,listing:String?,booking:String?)=auth.authorized("RENTER") { a,h->
        require(type in UNLOCKS || type=="BOOKING"); a.ownerCheckout(h,key,OwnerCheckout(type,listing,booking)) }
    suspend fun complete(v:OwnerPayment)=auth.authorized("RENTER") { a,h->a.completeOwnerMock(h,MockCompletion(requireNotNull(v.providerReference),"SUCCESS")) }
    suspend fun status(id:String)=auth.authorized("RENTER") { a,h->a.paymentStatus(h,PaymentStatusBody(id)) }
    suspend fun location(id:String)=auth.authorized("RENTER") { a,h->a.unlockedLocation(h,id) }
    suspend fun phone(id:String)=auth.authorized("RENTER") { a,h->a.unlockedPhone(h,id) }
    suspend fun videos(id:String)=auth.authorized("RENTER") { a,h->a.unlockedVideos(h,id) }
    suspend fun applications()=auth.authorized("RENTER") { a,h->a.renterApplications(h).map { RenterApplication("LANDLORD",it) }+a.renterSharedApplications(h).map { RenterApplication("SUBLESSOR",it) } }
    suspend fun apply(v:RenterHome)=auth.authorized("RENTER") { a,h->RenterApplication(v.source,a.renterApply(h,kind(v.source),v.id)) }
    suspend fun withdraw(v:RenterApplication)=auth.authorized("RENTER") { a,h->a.renterWithdraw(h,kind(v.source),v.value.id) }
    suspend fun book(v:RenterApplication)=auth.authorized("RENTER") { a,h->a.renterBook(h,if(v.source=="LANDLORD") "landlord-applications" else "sublessor-applications",v.value.id) }
    suspend fun booking(id:String)=auth.authorized("RENTER") { a,h->a.renterBooking(h,id) }
    suspend fun moveIn(id:String)=auth.authorized("RENTER") { a,h->a.renterMoveIn(h,id) }
    suspend fun agree(id:String)=auth.authorized("RENTER") { a,h->a.renterAgreement(h,id) }
    suspend fun history(page:Int)=auth.authorized("RENTER") { a,h->a.ownerPayments(h,page) }
    suspend fun rentalThreads(page:Int)=auth.authorized("RENTER") { a,h->a.renterThreads(h,page) }
    suspend fun roomThreads(page:Int)=auth.authorized("RENTER") { a,h->a.renterRoomThreads(h,page) }
    suspend fun messages(t:RenterThread,cursor:String?)=auth.authorized("RENTER") { a,h->
        if(t.source=="LANDLORD") a.renterMessages(h,t.id,cursor)
        else a.renterRoomMessages(h,t.id,cursor).let { OwnerMessagePage(it.items,it.nextCursor,it.hasMore) } }
    suspend fun socket(source:String,events:(ChatEvent)->Unit)=auth.authorized("RENTER") { _,h->
        RentalChatSocket(com.makaohub.android.BuildConfig.API_BASE_URL,h,if(source=="LANDLORD") ChatChannel.RENTAL else ChatChannel.ROOMMATE,events).also { it.connect() } }
    private val transfer=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(90,TimeUnit.SECONDS).build()
    suspend fun upload(ticket:VerificationUploadTicket,body:RequestBody)=withContext(Dispatchers.IO) {
        transfer.newCall(signedVerificationRequest(ticket,body)).execute().use { if(!it.isSuccessful) throw MediaTransferFailed() }
    }
    private fun kind(source:String):String { require(source in listOf("LANDLORD","SUBLESSOR")); return if(source=="LANDLORD") "applications" else "sublessor-applications" }
    private fun retrofit2.Response<Unit>.check() { if(!isSuccessful) throw HttpException(this) }
    companion object { val UNLOCKS=setOf("EXACT_LOCATION_UNLOCK","VIDEO_UNLOCK","PHONE_UNLOCK") }
}
