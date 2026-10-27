package com.makaohub.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.util.UUID

enum class RenterScreen { HOME,AREA,FILTERS,RESULTS,DETAIL,REPORT,SAVED,VERIFICATION,APPLY,APPLICATIONS,APPLICATION,BOOKING,UNLOCK_INTRO,CHECKOUT,PAYMENT,ACCESS,HISTORY,INBOX,CHAT,PROFILE }
private class RenterUnlockPriceUnavailable : Exception()
private class RenterBookingPriceUnavailable : Exception()
class RenterViewModel(private val repo:RenterRepository,private val expired:()->Unit):ViewModel() {
    var screen by mutableStateOf(RenterScreen.HOME); private set
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var verification by mutableStateOf<OwnerVerification?>(null); private set
    var documents by mutableStateOf<List<OwnerVerificationDocument>>(emptyList()); private set
    var filter by mutableStateOf(HomeFilter())
    var homes by mutableStateOf<HomePage?>(null); private set
    var selected by mutableStateOf<RenterHome?>(null); private set
    var photos by mutableStateOf<List<HomeMedia>>(emptyList()); private set
    var applications by mutableStateOf<List<RenterApplication>>(emptyList()); private set
    var application by mutableStateOf<RenterApplication?>(null); private set
    var booking by mutableStateOf<RenterBooking?>(null); private set
    var payment by mutableStateOf<OwnerPayment?>(null); private set
    var price by mutableStateOf<UnlockPrice?>(null); private set
    var unlockType by mutableStateOf("EXACT_LOCATION_UNLOCK"); private set
    var location by mutableStateOf<OwnerLocation?>(null); private set
    var phone by mutableStateOf<UnlockedPhone?>(null); private set
    var videos by mutableStateOf<List<HomeMedia>>(emptyList()); private set
    var history by mutableStateOf<OwnerPage<OwnerPayment>?>(null); private set
    var threads by mutableStateOf<List<RenterThread>>(emptyList()); private set
    var thread by mutableStateOf<RenterThread?>(null); private set
    var messages by mutableStateOf<OwnerMessagePage?>(null); private set
    var chatDraft by mutableStateOf("")
    var chatReady by mutableStateOf(false); private set
    var awaitingEcho by mutableStateOf(false); private set
    var reportCategory by mutableStateOf("MISLEADING_CONTENT")
    var reportDescription by mutableStateOf("")
    var threadPage by mutableIntStateOf(0); private set
    var moreThreads by mutableStateOf(false); private set
    var pendingEvidence by mutableStateOf<String?>(null); private set
    var evidenceProgress by mutableStateOf<Int?>(null); private set
    var evidenceStage by mutableStateOf<String?>(null); private set
    val evidenceNames=mutableStateMapOf<RenterEvidence,String>()
    val pendingEvidenceType: String? get() = ticket?.documentType
    private var bytes:ByteArray?=null
    private var ticket:VerificationUploadTicket?=null
    private var transferred=false
    private var socket:RentalChatSocket?=null
    private var generation=0L
    private var work:Job?=null
    private var messageId:String?=null
    private var attemptContext:String?=null
    private var attemptKey=UUID.randomUUID().toString()
    private var attemptUnknown=false
    private var attemptPayment:OwnerPayment?=null
    private var visible=true
    private var accessListingId:String?=null
    fun start()=run { refreshEvidence(); if(verification?.status!="VERIFIED") screen=RenterScreen.VERIFICATION else homes=repo.homes(filter) }
    private suspend fun refreshEvidence() { verification=repo.verification(); documents=repo.documents() }
    fun refreshVerification()=run { refreshEvidence() }
    fun go(target:RenterScreen) {
        if(busy) return
        disconnect(); clearAccess(); notice=null; screen=target
        run { when(target) {
            RenterScreen.HOME,RenterScreen.RESULTS -> { refreshEvidence(); homes=repo.homes(filter) }
            RenterScreen.SAVED -> homes=repo.homes(filter,saved=true)
            RenterScreen.VERIFICATION -> refreshEvidence()
            RenterScreen.APPLICATIONS -> applications=repo.applications()
            RenterScreen.HISTORY -> history=repo.history(0)
            RenterScreen.INBOX -> loadThreads(0)
            else -> Unit
        } }
    }
    fun search() { filter.error()?.let { notice=it; return }; go(RenterScreen.RESULTS) }
    fun nextHomes()=run { val old=homes?:return@run; if(old.hasNext) homes=repo.homes(filter,old.page+1,screen==RenterScreen.SAVED) }
    fun open(value:RenterHome)=run { clearAccess(); selected=repo.home(value); photos=repo.photos(requireNotNull(selected)); screen=RenterScreen.DETAIL }
    suspend fun cover(value:RenterHome)=repo.photos(value).firstOrNull()
    fun save(remove:Boolean)=run { repo.save(requireNotNull(selected),remove); notice=if(remove) "Removed from saved homes." else "Home saved." }
    fun save(value:RenterHome,remove:Boolean=false)=run { repo.save(value,remove); notice=if(remove) "Removed from saved homes." else "Home saved." }
    fun openReport() { if(!busy) { reportCategory="MISLEADING_CONTENT";reportDescription="";screen=RenterScreen.REPORT } }
    fun submitReport()=run {
        val response=repo.report(requireNotNull(selected),reportCategory,reportDescription)
        notice="Report ${response.state.lowercase()}.";reportDescription="";screen=RenterScreen.DETAIL
    }
    fun beginApply() { if(verification?.status!="VERIFIED") go(RenterScreen.VERIFICATION) else go(RenterScreen.APPLY) }
    fun apply()=run { application=repo.apply(requireNotNull(selected)); applications=repo.applications(); screen=RenterScreen.APPLICATION }
    fun openApplication(value:RenterApplication) { if(!busy) { application=value; screen=RenterScreen.APPLICATION } }
    fun withdraw()=run { val old=requireNotNull(application); application=RenterApplication(old.source,repo.withdraw(old)); applications=repo.applications() }
    fun book()=run {
        booking=try { repo.book(requireNotNull(application)) } catch(e:HttpException) {
            if(e.code()==503) throw RenterBookingPriceUnavailable()
            throw e
        }
        screen=RenterScreen.BOOKING
    }
    fun refreshBooking()=run { booking=repo.booking(requireNotNull(booking).id) }
    fun moveIn()=run { booking=repo.moveIn(requireNotNull(booking).id) }
    fun agree()=run { booking=repo.agree(requireNotNull(booking).id) }
    fun openAccess(type:String)=run {
        require(type in RenterRepository.UNLOCKS); clearAccess()
        val id=requireNotNull(selected).id
        try { readAccess(type,id); unlockType=type; accessListingId=id; screen=RenterScreen.ACCESS }
        catch(e:HttpException) { if(e.code()!=403) throw e; prepareUnlock(type,id); screen=RenterScreen.UNLOCK_INTRO }
    }
    fun continueToCheckout() { if(!busy && price!=null) screen=RenterScreen.CHECKOUT }
    private suspend fun readAccess(type:String,id:String) { when(type) {
        "EXACT_LOCATION_UNLOCK" -> location=repo.location(id)
        "PHONE_UNLOCK" -> phone=repo.phone(id)
        "VIDEO_UNLOCK" -> videos=repo.videos(id)
    } }
    private suspend fun prepareUnlock(type:String,id:String) {
        ensureAttempt("$type:$id")
        price=try { repo.price(type) } catch(e:HttpException) {
            if(e.code()==503) throw RenterUnlockPriceUnavailable()
            throw e
        }; unlockType=type; accessListingId=id; screen=RenterScreen.CHECKOUT
    }
    fun bookingCheckout()=run { val id=requireNotNull(booking).id; ensureAttempt("BOOKING:$id"); price=null; unlockType="BOOKING"; screen=RenterScreen.CHECKOUT }
    private fun ensureAttempt(context:String) {
        if(context!=attemptContext) {
            check(!attemptUnknown && attemptPayment?.status !in listOf("PENDING","CREATED")) { "Refresh your pending payment before starting another." }
            attemptContext=context; attemptKey=UUID.randomUUID().toString(); payment=null
            attemptPayment=null
        } else if(attemptPayment?.status=="FAILED") { attemptKey=UUID.randomUUID().toString(); payment=null; attemptPayment=null }
        else payment=attemptPayment
    }
    fun createPayment()=run {
        check(BuildConfig.MOCK_PAYMENTS_ENABLED || unlockType in RenterRepository.UNLOCKS) {
            "Booking payments require the regulated custody arrangement."
        }
        if(payment!=null) { screen=RenterScreen.PAYMENT; return@run }
        attemptUnknown=true // Keep the same key after timeout; never silently make a new charge.
        payment=repo.checkout(attemptKey,unlockType,if(unlockType=="BOOKING") null else accessListingId,if(unlockType=="BOOKING") booking?.id else null)
        attemptPayment=payment; attemptUnknown=false; screen=RenterScreen.PAYMENT
    }
    fun simulatePayment(result:MockPaymentResult)=run {
        payment=repo.complete(requireNotNull(payment),result); rememberAttemptStatus(); refreshPaidBooking()
    }
    fun refreshPayment()=run {
        val current=requireNotNull(payment)
        payment=if(!BuildConfig.MOCK_PAYMENTS_ENABLED && current.status=="PENDING")
            repo.reconcile(requireNotNull(current.providerReference)) else repo.status(current.id)
        rememberAttemptStatus();refreshPaidBooking()
    }
    private fun rememberAttemptStatus() { if(payment?.id==attemptPayment?.id) { attemptPayment=payment; attemptUnknown=false } }
    private suspend fun refreshPaidBooking() { payment?.bookingId?.let { booking=repo.booking(it) } }
    fun viewPaidAccess()=run {
        val p=requireNotNull(payment); check(p.status=="SUCCEEDED" && p.paymentType in RenterRepository.UNLOCKS)
        clearAccess(); readAccess(p.paymentType,requireNotNull(p.listingId))
        unlockType=p.paymentType; accessListingId=p.listingId; screen=RenterScreen.ACCESS
    }
    fun openPayment(value:OwnerPayment)=run { clearAccess(); payment=repo.status(value.id); unlockType=value.paymentType; accessListingId=value.listingId; value.bookingId?.let { booking=repo.booking(it) }; screen=RenterScreen.PAYMENT }
    fun nextHistory()=run { val old=history?:return@run; if(old.hasNext) { val p=repo.history(old.page+1); history=OwnerPage(old.items+p.items,p.page,p.size,p.totalElements,p.totalPages,p.hasNext) } }
    private suspend fun loadThreads(page:Int) {
        val rental=repo.rentalThreads(page); val shared=repo.roomThreads(page)
        threads=(if(page==0) emptyList() else threads)+rental.items.map { RenterThread(it.id,"LANDLORD",status=it.status) }+
            shared.content.map { RenterThread(it.id,"SUBLESSOR",it.chatUnlocked,it.status) }
        threadPage=page; moreThreads=rental.hasNext || !shared.last
    }
    fun nextThreads()=run { if(moreThreads) loadThreads(threadPage+1) }
    fun openThread(value:RenterThread) {
        run {
            disconnect(); thread=value; messages=repo.messages(value,null); screen=RenterScreen.CHAT
            if (value.status=="OPEN") connect(value)
        }
    }
    private suspend fun connect(value:RenterThread) {
        val current=++generation
        socket=repo.socket(value.source) { event->viewModelScope.launch {
            if(current!=generation) return@launch
            when(event) {
                ChatEvent.Connected -> chatReady=true
                is ChatEvent.Unavailable -> { chatReady=false; awaitingEcho=false; notice=event.text }
                is ChatEvent.Message -> if(event.message.threadId==value.id) {
                    val old=messages?:OwnerMessagePage(emptyList(),null,false)
                    messages=OwnerMessagePage((listOf(event.message)+old.items).distinctBy { it.id }.take(500),old.nextCursor,old.hasNext)
                    if(event.message.clientMessageId==messageId) { awaitingEcho=false; chatDraft=""; messageId=null }
                }
            }
        } }
    }
    fun reconnect()=run { disconnect(); thread?.takeIf { it.status=="OPEN" }?.let { connect(it) } }
    fun olderMessages()=run { val old=messages?:return@run; if(old.hasNext) { val p=repo.messages(requireNotNull(thread),old.nextCursor); messages=OwnerMessagePage((old.items+p.items).distinctBy { it.id },p.nextCursor,p.hasNext) } }
    fun send() { if(!chatReady || awaitingEcho || chatDraft.isBlank() || chatDraft.length>2000) return
        val id=messageId?:UUID.randomUUID().toString().also { messageId=it }; awaitingEcho=true
        if(socket?.send(requireNotNull(thread).id,id,chatDraft)!=true) { awaitingEcho=false; notice="Reconnect before sending." }
    }
    fun disconnect() { generation++; socket?.close(); socket=null; chatReady=false; awaitingEcho=false }
    fun background() { visible=false; disconnect(); clearAccess() }
    fun foreground() { visible=true; if(!busy && screen==RenterScreen.ACCESS) run { clearAccess(); readAccess(unlockType,requireNotNull(accessListingId)) } }
    private fun clearAccess() { location=null; phone=null; videos=emptyList() }
    fun upload(type:RenterEvidence,resolver:ContentResolver,uri:Uri)=run {
        evidenceStage="Preparing image"
        val (name,value)=withContext(Dispatchers.IO) { evidenceDisplayName(resolver,uri) to VerificationImages.fromUri(resolver,uri) }; beginEvidence(type,value,name)
    }
    fun capture(type:RenterEvidence,bitmap:Bitmap) { if(busy) { bitmap.recycle(); return }; run {
        evidenceStage="Preparing photo"
        try { beginEvidence(type,withContext(Dispatchers.IO) { VerificationImages.fromBitmap(bitmap) },when(type) {
            RenterEvidence.ID_FRONT->"national_id_front.jpg";RenterEvidence.ID_BACK->"national_id_back.jpg";else->"selfie_capture.jpg"
        }) } finally { bitmap.recycle() }
    } }
    internal suspend fun beginEvidence(type:RenterEvidence,value:ByteArray,displayName:String?=null) {
        if(verification?.status !in listOf("NOT_STARTED","REJECTED")) { value.fill(0); notice="Refresh verification before uploading."; return }
        val fresh=try { repo.ticket(type,true) } catch(e:Exception) { value.fill(0); throw e }
        if(fresh.documentType!=type.name || fresh.contentType!="image/jpeg") { value.fill(0); error("Unexpected evidence format") }
        bytes?.fill(0); documents=documents.filterNot { it.documentType==type.name }
        evidenceNames.remove(type); displayName?.let { evidenceNames[type]=it }
        ticket=fresh; bytes=value; transferred=false; pendingEvidence=fresh.documentId; evidenceProgress=null; finishEvidence()
    }
    fun retryEvidence()=run { val old=ticket?:return@run; if(!transferred) {
        val fresh=repo.ticket(RenterEvidence.valueOf(old.documentType),false)
        require(fresh.documentId==old.documentId && fresh.documentType==old.documentType && fresh.contentType==old.contentType); ticket=fresh
    }; finishEvidence() }
    private suspend fun finishEvidence() { val t=requireNotNull(ticket)
        if(!transferred) { evidenceStage="Uploading"; repo.upload(t,EvidenceUploadBody(requireNotNull(bytes),"image/jpeg".toMediaType()) {
            if(pendingEvidence==t.documentId) evidenceProgress=it
        }); transferred=true }
        evidenceStage="Checking document"
        confirm(t.documentId); bytes?.fill(0); bytes=null; ticket=null; transferred=false; pendingEvidence=null; evidenceProgress=null
    }
    fun confirmStored(id:String)=run {
        confirm(id)
        if(pendingEvidence==id) { bytes?.fill(0); bytes=null; ticket=null; transferred=false; pendingEvidence=null; evidenceProgress=null }
    }
    private suspend fun confirm(id:String) {
        try { repo.confirm(id) } catch(e:HttpException) {
            if(e.code()!=409 || repo.documents().none { it.documentId==id && it.status=="READY" && it.securityChecked }) throw e
        }; documents=repo.documents()
    }
    fun submitEvidence()=run { check(renterEvidenceComplete(documents)); verification=repo.submit() }
    fun say(value:String) { notice=value }
    fun dismissNotice() { notice=null }
    private fun run(action:suspend ()->Unit) {
        if(busy) return
        busy=true; notice=null
        work=viewModelScope.launch { try { action() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                if(e is SessionExpired || e is WorkspaceUnavailable) { clear(); expired() }
                else notice=when {
                    e is RenterUnlockPriceUnavailable -> "Unlock pricing is not configured. Use the backend mock profile for test payments, or configure approved prices for live payments."
                    e is RenterBookingPriceUnavailable -> "Booking deposit pricing is not configured. Use the backend mock profile for the illustrative rent-based deposit, or configure an approved live deposit."
                    e is HttpException -> when(e.code()) {
                    400->"Check your file or required details."; 403->"Your verification or access does not permit this action."
                    404->"This record or test service is unavailable."; 409->"The saved state changed. Refresh and try again."
                    503->"Document checks or test pricing are unavailable. Please try later."; else->friendlyError(e)
                    }
                    else -> friendlyError(e)
                }
            } finally { if(!visible) clearAccess(); busy=false; evidenceStage=null }
        }
    }
    fun clear() { work?.cancel(); disconnect(); clearAccess(); bytes?.fill(0); bytes=null; ticket=null; pendingEvidence=null; transferred=false; evidenceProgress=null; evidenceStage=null
        documents=emptyList(); evidenceNames.clear(); verification=null; selected=null; photos=emptyList(); homes=null; applications=emptyList(); application=null
        booking=null; payment=null; price=null; history=null; threads=emptyList(); thread=null; messages=null; chatDraft=""; busy=false
    }
    override fun onCleared() { clear() }
}
