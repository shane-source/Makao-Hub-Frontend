package com.makaohub.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.util.UUID

enum class RenterScreen { HOME,AREA,FILTERS,RESULTS,DETAIL,REPORT,SAVED,VERIFICATION,APPLY,APPLICATIONS,APPLICATION,BOOKING,UNLOCK_INTRO,CHECKOUT,PAYMENT,ACCESS,HISTORY,INBOX,CHAT,PROFILE }
private class RenterUnlockPriceUnavailable : Exception()
private class RenterBookingPriceUnavailable : Exception()
private class PaymentStillPending : Exception()
class RenterViewModel(private val repo:RenterRepository,private val recent:OfflineHomes? = null,private val expired:()->Unit):ViewModel() {
    var screen by mutableStateOf(RenterScreen.HOME); private set
    var offline by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var verification by mutableStateOf<OwnerVerification?>(null); private set
    var documents by mutableStateOf<List<OwnerVerificationDocument>>(emptyList()); private set
    var filter by mutableStateOf(HomeFilter())
    var homes by mutableStateOf<HomePage?>(null); private set
    private val savedHomes = mutableStateMapOf<Pair<String, String>, Boolean>()
    var selected by mutableStateOf<RenterHome?>(null); private set
    var photos by mutableStateOf<List<HomeMedia>>(emptyList()); private set
    var listingAmenities by mutableStateOf<List<AmenityOption>>(emptyList()); private set
    var applications by mutableStateOf<List<RenterApplication>>(emptyList()); private set
    var application by mutableStateOf<RenterApplication?>(null); private set
    var applicationRemark by mutableStateOf<String?>(null); private set
    var remarkVisible by mutableStateOf(false); private set
    var booking by mutableStateOf<RenterBooking?>(null); private set
    var payment by mutableStateOf<OwnerPayment?>(null); private set
    var price by mutableStateOf<UnlockPrice?>(null); private set
    var unlockType by mutableStateOf("EXACT_LOCATION_UNLOCK"); private set
    var location by mutableStateOf<OwnerLocation?>(null); private set
    var phone by mutableStateOf<UnlockedPhone?>(null); private set
    var videos by mutableStateOf<List<HomeMedia>>(emptyList()); private set
    var history by mutableStateOf<OwnerPage<OwnerPayment>?>(null); private set
    var threads by mutableStateOf<List<RenterThread>>(emptyList()); private set
    var chatStates by mutableStateOf<Map<String,ChatThreadState>>(emptyMap()); private set
    var showArchived by mutableStateOf(false)
    var applicationStates by mutableStateOf<Map<String,String>>(emptyMap()); private set
    var showArchivedApps by mutableStateOf(false)
    private fun appKey(a:RenterApplication)="${a.source}:${a.value.id}"
    val visibleApplications:List<RenterApplication> get()=applications.filter { applicationStates[appKey(it)]==null }
    val archivedApplications:List<RenterApplication> get()=applications.filter { applicationStates[appKey(it)]=="ARCHIVED" }
    private suspend fun loadApplicationStates() {
        try { applicationStates=repo.applicationStates().associate { "${it.kind}:${it.applicationId}" to it.state } } catch(e:CancellationException) { throw e } catch(_:Exception) { }
    }
    private fun changeApplication(a:RenterApplication,state:String,done:String)=run { repo.setApplicationState(a.source,a.value.id,state); loadApplicationStates(); notice=done }
    fun archiveApplication(a:RenterApplication)=changeApplication(a,"ARCHIVED","Application archived. Find it under Archived.")
    fun restoreApplication(a:RenterApplication)=changeApplication(a,"ACTIVE","Application moved back to your list.")
    fun deleteApplication(a:RenterApplication)=changeApplication(a,"DELETED","Application deleted from your list. The host still keeps their record.")
    private fun kindOf(t:RenterThread)=if(t.source=="LANDLORD") ChatStates.RENTAL else ChatStates.ROOMMATE
    private fun stateOf(t:RenterThread)=chatStates[ChatStates.key(kindOf(t),t.id)]
    private fun placement(t:RenterThread)=ChatStates.placement(stateOf(t),t.lastMessageAt)
    val inboxThreads:List<RenterThread> get()=threads.filter { placement(it)==ChatStates.ACTIVE }
    val archivedThreads:List<RenterThread> get()=threads.filter { placement(it)==ChatStates.ARCHIVED }
    private suspend fun loadStates() {
        try { chatStates=repo.chatStates().associateBy { ChatStates.key(it.kind,it.threadId) } } catch(e:CancellationException) { throw e } catch(_:Exception) { }
    }
    private fun changeState(t:RenterThread,state:String,done:String)=run { repo.setChatState(kindOf(t),t.id,state); loadStates(); notice=done }
    fun archiveThread(t:RenterThread)=changeState(t,ChatStates.ARCHIVED,"Conversation archived. Find it under Archived.")
    fun restoreThread(t:RenterThread)=changeState(t,ChatStates.ACTIVE,"Conversation moved back to your inbox.")
    fun deleteThread(t:RenterThread)=changeState(t,ChatStates.DELETED,"Conversation deleted for you. The other person still has it.")
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
    private val live=LiveChat(viewModelScope,listOf(ChatChannel.RENTAL,ChatChannel.ROOMMATE),{ channel,events->repo.liveSocket(channel,events) }) { channel,event->viewModelScope.launch { liveEvent(channel,event) } }
    private var threadRefresh:Job?=null
    private fun channelOf(value:RenterThread)=if(value.source=="LANDLORD") ChatChannel.RENTAL else ChatChannel.ROOMMATE
    private fun liveSocket()=thread?.let { live.socket(channelOf(it)) }
    private var work:Job?=null
    private var messageId:String?=null
    private var attemptContext:String?=null
    private var attemptKey=UUID.randomUUID().toString()
    private var attemptUnknown=false
    private var attemptPayment:OwnerPayment?=null
    private val unlockedKeys=mutableStateListOf<String>()
    /** True once this person has opened or paid for this item on this home, so the card can show a tick instead of a lock. */
    fun isUnlocked(type:String)=selected?.id?.let { "$type:$it" in unlockedKeys }==true
    private fun markUnlocked(type:String,listingId:String?) { if(listingId!=null && "$type:$listingId" !in unlockedKeys) unlockedKeys.add("$type:$listingId") }
    private fun markPaid(p:OwnerPayment?) { if(p!=null && p.status=="SUCCEEDED" && p.paymentType in RenterRepository.UNLOCKS) markUnlocked(p.paymentType,p.listingId) }
    private var visible=true
    private var accessListingId:String?=null
    fun consumeOffline() { offline = false }
    fun start()=run(offlineOnFailure=true) {
        // Show the last list straight away; the fresh one below replaces it as soon as it arrives.
        if(homes==null && filter==HomeFilter()) recent?.readRecent()?.takeIf { it.isNotEmpty() }?.let { homes=HomePage(it,0,false) }
        // Start the homes request together with the verification checks instead of after them.
        // If the homes request fails here it is simply repeated below, so errors still surface as before.
        coroutineScope {
            val early=async { try { repo.homes(filter) } catch(e:CancellationException) { throw e } catch(_:Exception) { null } }
            refreshEvidence()
            if(verification?.status!="VERIFIED") { early.cancel(); screen=RenterScreen.VERIFICATION }
            else homes=early.await() ?: repo.homes(filter)
        }
    }
    private suspend fun refreshEvidence() { coroutineScope {
        val v=async { repo.verification() }; val d=async { repo.documents() }
        verification=v.await(); documents=d.await()
    } }
    fun refreshVerification()=run { refreshEvidence() }
    fun go(target:RenterScreen) {
        if(busy) return
        disconnect(); clearAccess(); notice=null; screen=target
        run(offlineOnFailure = target in listOf(RenterScreen.HOME, RenterScreen.RESULTS)) { when(target) {
            RenterScreen.HOME,RenterScreen.RESULTS -> coroutineScope {
                val list=async { repo.homes(filter) }
                refreshEvidence(); homes=list.await()
            }
            RenterScreen.SAVED -> { homes=repo.homes(filter,saved=true); homes?.items?.forEach { savedHomes[it.source to it.id]=true } }
            RenterScreen.VERIFICATION -> refreshEvidence()
            RenterScreen.APPLICATIONS -> { applications=repo.applications(); loadApplicationStates() }
            RenterScreen.HISTORY -> history=repo.history(0)
            RenterScreen.INBOX -> loadThreads(0)
            else -> Unit
        } }
    }
    fun search() { filter.error()?.let { notice=it; return }; go(if(screen==RenterScreen.SAVED) RenterScreen.SAVED else RenterScreen.RESULTS) }
    fun nextHomes()=run { val old=homes?:return@run; if(old.hasNext) { homes=repo.homes(filter,old.page+1,screen==RenterScreen.SAVED); if(screen==RenterScreen.SAVED) homes?.items?.forEach { savedHomes[it.source to it.id]=true } } }
    fun open(value:RenterHome)=run { clearAccess(); coroutineScope {
        val detail=async { repo.home(value) }; val pics=async { repo.photos(value) }; val amenities=async { repo.listingAmenities(value) }
        selected=detail.await(); photos=pics.await(); listingAmenities=amenities.await(); screen=RenterScreen.DETAIL
    } }
    private val coverCache=HashMap<String,HomeMedia>()
    private val coverSlots=kotlinx.coroutines.sync.Semaphore(3)
    /** Cards ask for their cover as they appear. Answers are remembered and at most three are fetched at once, so the list never queues up ahead of taps. */
    suspend fun cover(value:RenterHome):HomeMedia? {
        val key="${value.source}:${value.id}"
        coverCache[key]?.let { return it }
        return coverSlots.withPermit { coverCache[key] ?: repo.photos(value).firstOrNull()?.also { coverCache[key]=it; recent?.saveCover(value.source,value.id,it.signedReadUrl) } }
    }
    fun isSaved(value:RenterHome)=savedHomes[value.source to value.id] == true
    fun save(remove:Boolean) { selected?.let { save(it,remove) } }
    fun save(value:RenterHome,remove:Boolean=false)=run {
        repo.save(value,remove); savedHomes[value.source to value.id]=!remove
        if(remove && screen==RenterScreen.SAVED) homes=repo.homes(filter,saved=true)
        notice=if(remove) "Removed from saved homes." else "Home saved."
    }
    fun openReport() { if(!busy) { reportCategory="MISLEADING_CONTENT";reportDescription="";screen=RenterScreen.REPORT } }
    fun submitReport()=run {
        val response=repo.report(requireNotNull(selected),reportCategory,reportDescription)
        notice="Report ${response.state.lowercase()}.";reportDescription="";screen=RenterScreen.DETAIL
    }
    fun beginApply() { if(verification?.status!="VERIFIED") go(RenterScreen.VERIFICATION) else go(RenterScreen.APPLY) }
    fun apply(moveInDate:String,message:String)=run { application=repo.apply(requireNotNull(selected),moveInDate,message.trim().ifBlank { null }); applications=repo.applications(); screen=RenterScreen.APPLICATION }
    var applicationHome by mutableStateOf<RenterHome?>(null); private set
    var applicationCoverUrl by mutableStateOf<String?>(null); private set
    fun openApplication(value:RenterApplication) { if(!busy) {
        application=value; applicationRemark=null; remarkVisible=false; applicationHome=null; applicationCoverUrl=null; screen=RenterScreen.APPLICATION
        viewModelScope.launch {
            try { applicationHome=repo.homeById(value.source,value.value.listingId); applicationCoverUrl=repo.coverUrl(value.source,value.value.listingId) }
            catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { }
        }
    } }
    /** Opens the existing conversation with the host of this application. */
    fun messageHost()=run {
        val app=requireNotNull(application)
        if(threads.none { it.applicationId==app.value.id }) loadThreads(0)
        val found=threads.firstOrNull { it.applicationId==app.value.id }
        if(found==null) { notice="The conversation isn’t available yet."; return@run }
        openChat(found)
    }
    fun viewApplicationRemark()=run { applicationRemark=repo.applicationRemark(requireNotNull(application)); remarkVisible=true }
    fun withdraw()=run { val old=requireNotNull(application); application=RenterApplication(old.source,repo.withdraw(old)); applications=repo.applications() }
    fun book()=run {
        booking=try { repo.book(requireNotNull(application)) } catch(e:HttpException) {
            if(e.code()==503) throw RenterBookingPriceUnavailable()
            throw e
        }
        screen=RenterScreen.BOOKING
    }
    fun refreshBooking()=run { booking=repo.booking(requireNotNull(booking).id) }
    fun answerMoveIn(accepted:Boolean)=run {
        booking=repo.answerMoveIn(requireNotNull(booking).id,accepted,java.util.UUID.randomUUID().toString())
        notice=if(accepted) "Thank you! Your payment is being released to the owner." else "Noted. Your money stays safely held while our team reviews."
    }
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
    }; markUnlocked(type,id) }
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
            if(attemptUnknown || attemptPayment?.status in listOf("PENDING","CREATED")) throw PaymentStillPending()
            attemptContext=context; attemptKey=UUID.randomUUID().toString(); payment=null
            attemptPayment=null
        } else if(attemptPayment?.status=="FAILED") { attemptKey=UUID.randomUUID().toString(); payment=null; attemptPayment=null }
        else payment=attemptPayment
    }
    fun createPayment()=run {
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
        payment=if((!BuildConfig.MOCK_PAYMENTS_ENABLED || !current.authorizationUrl.isNullOrBlank()) && current.status=="PENDING")
            repo.reconcile(requireNotNull(current.providerReference)) else repo.status(current.id)
        rememberAttemptStatus();refreshPaidBooking()
    }
    private var watcher:Job?=null
    /** While a payment is open and unpaid, quietly asks Paystack every few seconds. On success it shows Paid and opens the unlocked item. */
    fun watchPayment() {
        if(watcher?.isActive==true) return
        watcher=viewModelScope.launch {
            var tries=0
            while(tries<60 && screen==RenterScreen.PAYMENT) {
                val current=payment ?: break
                if(current.status !in listOf("CREATED","PENDING")) break
                delay(4000); tries++
                if(!visible || busy || screen!=RenterScreen.PAYMENT) continue
                try {
                    val ref=current.providerReference
                    val next=if((!BuildConfig.MOCK_PAYMENTS_ENABLED || !current.authorizationUrl.isNullOrBlank()) && !ref.isNullOrBlank()) repo.reconcile(ref) else repo.status(current.id)
                    if(payment?.id!=current.id) break
                    payment=next; rememberAttemptStatus(); refreshPaidBooking()
                    if(next.status=="SUCCEEDED" && next.paymentType in RenterRepository.UNLOCKS && next.listingId!=null) {
                        try {
                            clearAccess(); readAccess(next.paymentType,next.listingId)
                            unlockType=next.paymentType; accessListingId=next.listingId; screen=RenterScreen.ACCESS
                        } catch(e:CancellationException) { throw e } catch(_:Exception) { /* stay on Paid; the View button still works */ }
                    }
                } catch(e:CancellationException) { throw e } catch(_:Exception) { }
            }
        }
    }
    private fun rememberAttemptStatus() { markPaid(payment); if(payment?.id==attemptPayment?.id) { attemptPayment=payment; attemptUnknown=false } }
    private suspend fun refreshPaidBooking() { payment?.bookingId?.let { booking=repo.booking(it) } }
    fun viewPaidAccess()=run {
        val p=requireNotNull(payment); check(p.status=="SUCCEEDED" && p.paymentType in RenterRepository.UNLOCKS)
        clearAccess(); readAccess(p.paymentType,requireNotNull(p.listingId))
        unlockType=p.paymentType; accessListingId=p.listingId; screen=RenterScreen.ACCESS
    }
    fun openPayment(value:OwnerPayment)=run { clearAccess(); payment=repo.status(value.id); unlockType=value.paymentType; accessListingId=value.listingId; value.bookingId?.let { booking=repo.booking(it) }; screen=RenterScreen.PAYMENT }
    fun nextHistory()=run { val old=history?:return@run; if(old.hasNext) { val p=repo.history(old.page+1); history=OwnerPage(old.items+p.items,p.page,p.size,p.totalElements,p.totalPages,p.hasNext) } }
    private suspend fun loadThreads(page:Int) {
        val (rental,shared)=coroutineScope { val a=async { repo.rentalThreads(page) }; val b=async { repo.roomThreads(page) }; val c=async { loadStates() }; c.await(); a.await() to b.await() }
        threads=(if(page==0) emptyList() else threads)+rental.items.map { RenterThread(it.id,"LANDLORD",status=it.status,name=it.counterpartyName,avatar=it.counterpartyAvatarUrl,lastMessage=it.lastMessage,lastMessageAt=it.lastMessageAt,applicationId=it.rentalApplicationId) }+
            shared.content.map { RenterThread(it.id,"SUBLESSOR",it.chatUnlocked,it.status,it.counterpartyName,it.counterpartyAvatarUrl,it.lastMessage,it.lastMessageAt,it.roommateInterestId) }
        threadPage=page; moreThreads=rental.hasNext || !shared.last
    }
    /** Quiet refresh for the tab badges; never shows a spinner or an error. */
    fun refreshBadges() { live.start(); if(busy) return; viewModelScope.launch {
        try {
            applications=repo.applications()
            loadApplicationStates()
            // An open application follows its latest status without leaving the screen.
            application?.let { cur -> applications.firstOrNull { it.source==cur.source && it.value.id==cur.value.id }?.let { application=it } }
            when(screen) {
                RenterScreen.VERIFICATION -> if(pendingEvidence==null && evidenceProgress==null) refreshEvidence()
                RenterScreen.HOME,RenterScreen.RESULTS -> verification=repo.verification()
                RenterScreen.BOOKING -> booking?.let { booking=repo.booking(it.id) }
                RenterScreen.HISTORY -> history=repo.history(0)
                else -> Unit
            }
            if(screen!=RenterScreen.INBOX && screen!=RenterScreen.CHAT) loadThreads(0)
        } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { }
    } }
    suspend fun applicationCover(a:RenterApplication):String?=repo.coverUrl(a.source,a.value.listingId)
    fun nextThreads()=run { if(moreThreads) loadThreads(threadPage+1) }
    fun openThread(value:RenterThread) { run { openChat(value) } }
    /** The chat opens at once; the live connection and the message history load side by side. */
    private suspend fun openChat(value:RenterThread) {
        thread=value; messages=null; screen=RenterScreen.CHAT; awaitingEcho=false
        chatReady=value.status=="OPEN" && live.isReady(channelOf(value))
        if(value.status=="OPEN") live.start()
        val page=repo.messages(value,null)
        if(thread?.id!=value.id) return
        messages=OwnerMessagePage(ChatStates.afterDelete((messages?.items.orEmpty()+page.items).distinctBy { it.id },stateOf(value)),page.nextCursor,page.hasNext)
    }
    /** Every message from the live connection lands here, whichever screen is showing. */
    private fun liveEvent(channel:ChatChannel,event:ChatEvent) {
        val open=thread
        val mine=open!=null && channelOf(open)==channel
        when(event) {
            ChatEvent.Connected -> if(mine) chatReady=true
            is ChatEvent.Unavailable -> if(mine) { chatReady=false; awaitingEcho=false }
            is ChatEvent.Message -> if(screen==RenterScreen.CHAT && open?.id==event.message.threadId) {
                val old=messages?:OwnerMessagePage(emptyList(),null,false)
                messages=OwnerMessagePage((listOf(event.message)+old.items.filter { it.id!=event.message.clientMessageId }).distinctBy { it.id }.take(500),old.nextCursor,old.hasNext)
            } else refreshThreads()
        }
    }
    /** A message arrived for a chat that is not open: refresh the inbox list and unread badge quietly. */
    private fun refreshThreads() {
        threadRefresh?.cancel()
        threadRefresh=viewModelScope.launch {
            delay(300)
            try { loadThreads(0) } catch(e:CancellationException) { throw e } catch(_:Exception) { }
        }
    }
    fun acknowledgeMessages(ids: List<String>) = thread?.id?.let { liveSocket()?.acknowledge(it, ids) } == true
    fun reconnect() { chatReady=false; live.restart() }
    fun olderMessages()=run { val old=messages?:return@run; if(old.hasNext) { val t=requireNotNull(thread); val p=repo.messages(t,old.nextCursor); messages=OwnerMessagePage(ChatStates.afterDelete((old.items+p.items).distinctBy { it.id },stateOf(t)),p.nextCursor,p.hasNext) } }
    fun send() {
        val text=chatDraft.trim(); if(text.isEmpty() || text.length>2000) return
        val threadId=thread?.id?:return; val id=UUID.randomUUID().toString()
        if(liveSocket()?.send(threadId,id,text)==true) {
            val old=messages?:OwnerMessagePage(emptyList(),null,false)
            messages=OwnerMessagePage(listOf(OwnerMessage(id, threadId, "__me__", id, text, false, java.time.Instant.now().toString()))+old.items,old.nextCursor,old.hasNext)
            chatDraft=""; messageId=null
        } else notice="Message not sent. Reconnecting…"
    }
    /** Leaving a chat screen only resets the screen state; the live connection stays open. */
    fun disconnect() { chatReady=false; awaitingEcho=false }
    fun background() { visible=false; live.stop(); disconnect(); clearAccess() }
    fun foreground() { visible=true; live.start(); if(!busy && screen==RenterScreen.ACCESS) run { clearAccess(); readAccess(unlockType,requireNotNull(accessListingId)) } }
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
    private fun run(offlineOnFailure:Boolean=false, action:suspend ()->Unit) {
        if(busy) return
        busy=true; notice=null
        work=viewModelScope.launch { try { action() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                if(e is SessionExpired || e is WorkspaceUnavailable) { clear(); expired() }
                else if (offlineOnFailure && (e is java.io.IOException || e is HttpException && e.code() >= 500)) offline = true
                else notice=when {
                    e is RenterUnlockPriceUnavailable -> "Pricing is not available right now. Please try again later."
                    e is RenterBookingPriceUnavailable -> "Booking pricing is not available right now. Please try again later."
                    e is PaymentStillPending -> "Your earlier payment is still pending. Open Payment history, tap Check payment on it, and try again once it shows as paid or failed."
                    e is HttpException -> when(e.code()) {
                    400->"Check your file or required details."; 403->"Your verification or access does not permit this action."
                    404->"This record is unavailable."; 409->"The saved state changed. Refresh and try again."
                    503->if(screen==RenterScreen.VERIFICATION) "ID checking is unavailable right now. Nothing has been approved. Please try again in a few minutes." else "This service is unavailable right now. Please try later."; else->friendlyError(e)
                    }
                    else -> friendlyError(e)
                }
            } finally { if(!visible) clearAccess(); busy=false; evidenceStage=null }
        }
    }
    fun clear() { work?.cancel(); live.stop(); disconnect(); clearAccess(); bytes?.fill(0); bytes=null; ticket=null; pendingEvidence=null; transferred=false; evidenceProgress=null; evidenceStage=null
        documents=emptyList(); evidenceNames.clear(); verification=null; selected=null; photos=emptyList(); listingAmenities=emptyList(); homes=null; coverCache.clear(); savedHomes.clear(); applications=emptyList(); application=null
        applicationRemark=null; remarkVisible=false; booking=null; payment=null; price=null; history=null; threads=emptyList(); chatStates=emptyMap(); showArchived=false; applicationStates=emptyMap(); showArchivedApps=false; thread=null; messages=null; chatDraft=""; busy=false
    }
    override fun onCleared() { clear() }
}
