package com.makaohub.android.ui

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makaohub.android.*
import com.makaohub.android.data.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.maplibre.android.geometry.LatLng
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@Composable fun RenterWorkspace(auth:AuthViewModel,profile:MyProfile) {
    val model:RenterViewModel=viewModel(key="renter-"+profile.id,factory=object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T =
            RenterViewModel(RenterRepository(auth.repository)) { auth.navigate(Page.LOGIN) } as T
    })
    val context=LocalContext.current
    val window=remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window }
    val sensitive=model.screen in listOf(RenterScreen.VERIFICATION,RenterScreen.ACCESS)
    DisposableEffect(window,sensitive) { if(sensitive) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if(sensitive) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(model,owner) {
        val listener=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP) model.background() else if(event==Lifecycle.Event.ON_RESUME) model.foreground() }
        owner.lifecycle.addObserver(listener)
        onDispose { owner.lifecycle.removeObserver(listener); model.clear() }
    }
    LaunchedEffect(model) { model.start() }
    val back={ model.go(when(model.screen) {
        RenterScreen.DETAIL,RenterScreen.FILTERS,RenterScreen.AREA -> RenterScreen.RESULTS
        RenterScreen.APPLY,RenterScreen.ACCESS,RenterScreen.CHECKOUT -> RenterScreen.DETAIL
        RenterScreen.APPLICATION -> RenterScreen.APPLICATIONS
        RenterScreen.CHAT -> RenterScreen.INBOX
        else -> RenterScreen.HOME
    }) }
    BackHandler(model.screen!=RenterScreen.HOME,onBack=back)
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(model.notice) { model.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() } }
    Scaffold(containerColor=Cream,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(model.screen!=RenterScreen.VERIFICATION) NavigationBar(containerColor=androidx.compose.ui.graphics.Color.White) {
            listOf(Triple(RenterScreen.HOME,"Home",Icons.Outlined.Home),Triple(RenterScreen.RESULTS,"Search",Icons.Outlined.Search),
                Triple(RenterScreen.APPLICATIONS,"Applications",Icons.Outlined.Description),Triple(RenterScreen.INBOX,"Messages",Icons.Outlined.ChatBubbleOutline),
                Triple(RenterScreen.PROFILE,"Profile",Icons.Outlined.PersonOutline)).forEach { (page,label,icon)->
                NavigationBarItem(selected=model.screen==page,onClick={model.go(page)},enabled=!model.busy,icon={Icon(icon,null,Modifier.size(21.dp))},label={Text(label,fontSize=10.sp)})
            }
        }
    }) { insets->
        val scroll=rememberScrollState()
        LaunchedEffect(model.screen) { scroll.scrollTo(0) }
        Column(Modifier.fillMaxSize().padding(insets).statusBarsPadding().imePadding().verticalScroll(scroll).padding(horizontal=22.dp).padding(bottom=24.dp)) {
            Row(Modifier.height(52.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(back,enabled=!model.busy,modifier=Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",tint=Brown,modifier=Modifier.size(18.dp)) }
                Spacer(Modifier.width(6.dp)); Text("makao hub",color=Brown,fontSize=15.sp,fontWeight=FontWeight.Bold)
            }
            if(model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Gap(12) }
            when(model.screen) {
                RenterScreen.HOME,RenterScreen.RESULTS,RenterScreen.SAVED -> RenterHomes(model,profile)
                RenterScreen.AREA,RenterScreen.FILTERS -> RenterFilters(model)
                RenterScreen.DETAIL -> RenterDetail(model)
                RenterScreen.VERIFICATION -> RenterVerification(model)
                RenterScreen.APPLY -> {
                    RenterHeading("Ready to apply?",model.selected?.title?:"")
                    SharedCard { SharedText("Your application"); SharedText(profile.fullName)
                        model.selected?.let { SharedText(kes(it.monthlyRentKes)+" / month") } }
                    var consent by rememberSaveable { mutableStateOf(false) }
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(consent,{consent=it},enabled=!model.busy); SharedText("I consent to submit this application") }
                    SharedNote("Submitting an application does not reserve the home or move money.")
                    SharedButton("Submit application",!model.busy && consent,model::apply)
                }
                RenterScreen.APPLICATIONS -> {
                    RenterHeading("Your applications","Keep track of every next step.")
                    model.applications.forEach { a->SharedCard {
                        SharedText(a.source.lowercase().replaceFirstChar { it.uppercase() }+" home"); SharedStatus(a.value.status)
                        SharedLink("View application",!model.busy) { model.openApplication(a) }
                    } }
                    if(model.applications.isEmpty()) SharedText("No applications yet.",true)
                }
                RenterScreen.APPLICATION -> RenterApplicationDetail(model)
                RenterScreen.BOOKING -> RenterBookingDetail(model)
                RenterScreen.CHECKOUT,RenterScreen.PAYMENT -> RenterCheckout(model)
                RenterScreen.ACCESS -> RenterAccess(model)
                RenterScreen.HISTORY -> {
                    RenterHeading("Payment history","Your test transactions")
                    MockNotice()
                    model.history?.items?.forEach { p->SharedCard { SharedText(p.paymentType.lowercase().replace('_',' ')); SharedStatus(p.status); SharedText(minor(p.amountMinor))
                        SharedLink("View",!model.busy) { model.openPayment(p) } } }
                    if(model.history?.hasNext==true) SharedLink("Load more",!model.busy,model::nextHistory)
                }
                RenterScreen.INBOX -> {
                    RenterHeading("Your messages","Protected conversations")
                    model.threads.forEach { t->SharedCard { SharedText(if(t.source=="LANDLORD") "Landlord conversation" else "Shared-home conversation")
                        SharedLink("Open chat",!model.busy && t.unlocked) { model.openThread(t) } } }
                    if(model.threads.isEmpty()) SharedText("No conversations yet.",true)
                    if(model.moreThreads) SharedLink("Load more",!model.busy,model::nextThreads)
                }
                RenterScreen.CHAT -> {
                    RenterHeading("Protected conversation",if(model.chatReady) "Connected" else "Disconnected")
                    if(!model.chatReady) SharedLink("Reconnect",!model.busy,model::reconnect)
                    model.messages?.items?.asReversed()?.forEach { m->SharedCard { SharedText(m.content); if(m.contactMasked) SharedText("Flagged · Contact details masked",true) } }
                    if(model.messages?.hasNext==true) SharedLink("Older messages",!model.busy,model::olderMessages)
                    RenterInput("Message",model.chatDraft,!model.awaitingEcho) { model.chatDraft=it.take(2000) }
                    SharedButton(if(model.awaitingEcho) "Sending…" else "Send",model.chatReady && !model.awaitingEcho && model.chatDraft.isNotBlank(),model::send)
                }
                RenterScreen.PROFILE -> {
                    RenterHeading("Your profile","Renter")
                    SharedCard { SharedText(profile.fullName); SharedText(profile.email,true); SharedStatus(model.verification?.status?:"NOT_STARTED") }
                    SharedLink("Verification",!model.busy) { model.go(RenterScreen.VERIFICATION) }
                    SharedLink("Saved homes",!model.busy) { model.go(RenterScreen.SAVED) }
                    SharedLink("Payment history",!model.busy) { model.go(RenterScreen.HISTORY) }
                    SharedLink("Sign out",!model.busy) { model.clear(); auth.logout() }
                }
            }
        }
    }
}

@Composable private fun RenterHomes(m:RenterViewModel,p:MyProfile) {
    RenterHeading(when(m.screen) { RenterScreen.HOME->"Find your next chapter"; RenterScreen.SAVED->"Homes you’re saving"; else->"Homes around "+m.filter.area.ifBlank { "you" } },
        if(m.screen==RenterScreen.HOME) "Karibu, "+p.fullName.substringBefore(' ')+". Let’s find somewhere that feels right." else "Whole homes and shared spaces")
    SharedStatus(if(m.verification?.status=="VERIFIED") "Verified Renter" else m.verification?.status?:"NOT_STARTED"); Gap(12)
    SharedCard { SharedLink(m.filter.area.ifBlank { "Choose an area" },!m.busy) { m.go(RenterScreen.AREA) }
        SharedLink("Search homes or rooms",!m.busy) { m.go(RenterScreen.FILTERS) } }
    Row { listOf(null to "All homes","LANDLORD" to "Whole homes","SUBLESSOR" to "Shared spaces").forEach { (source,label)->
        FilterChip(m.filter.source==source,{m.filter=m.filter.copy(source=source);m.search()},label={Text(label,fontSize=10.sp)},enabled=!m.busy) } }
    m.homes?.items?.forEach { home->
        SharedCard {
            val cover by produceState<HomeMedia?>(null,home.id,home.source) { value=try { m.cover(home) } catch(e:CancellationException) { throw e } catch(_:Exception) { null } }
            cover?.let { RemoteHomePhoto(it.signedReadUrl) }
            SharedText("Verified "+home.source.lowercase().replaceFirstChar { it.uppercase() },true)
            SharedText(home.title); SharedText(home.areaLabel+" · "+home.propertyType.lowercase().replace('_',' '),true)
            SharedText(kes(home.monthlyRentKes)+" / month"); SharedText("Exact location locked",true)
            SharedLink("View home",!m.busy) { m.open(home) }
        }
    }
    if(!m.busy && m.homes?.items?.isEmpty()==true) SharedCard { SharedText("No homes match yet."); SharedLink("Clear filters",true) { m.filter=HomeFilter();m.search() } }
    if(m.homes?.hasNext==true) SharedLink("Next page",!m.busy,m::nextHomes)
    if(m.screen==RenterScreen.HOME) SharedLink("Saved homes · View all",!m.busy) { m.go(RenterScreen.SAVED) }
}
@Composable private fun RenterFilters(m:RenterViewModel) {
    RenterHeading(if(m.screen==RenterScreen.AREA) "Where would you like to live?" else "Make it your kind of home","Choose an approximate area.")
    RenterInput("Search area",m.filter.area,!m.busy) { m.filter=m.filter.copy(area=it) }
    if(m.screen==RenterScreen.AREA) listOf("Nairobi","Rongai","Thika Road","Mombasa").forEach { area->
        SharedLink(area,!m.busy) { m.filter=m.filter.copy(area=area) }
    } else {
        RenterInput("Minimum rent · KSh",m.filter.min,!m.busy) { m.filter=m.filter.copy(min=it) }
        RenterInput("Maximum rent · KSh",m.filter.max,!m.busy) { m.filter=m.filter.copy(max=it) }
        RenterInput("Minimum bedrooms · Whole homes",m.filter.bedrooms,!m.busy) { m.filter=m.filter.copy(bedrooms=it) }
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(m.filter.furnished==true,{m.filter=m.filter.copy(furnished=if(it) true else null)},enabled=!m.busy); SharedText("Furnished") }
        listOf(null to "Both listing sources","LANDLORD" to "Landlord","SUBLESSOR" to "Sublessor").forEach { (source,title)->
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(m.filter.source==source,{m.filter=m.filter.copy(source=source)},enabled=!m.busy); SharedText(title) }
        }
    }
    SharedButton(if(m.screen==RenterScreen.AREA) "Use this area" else "Apply filters",!m.busy,m::search)
    SharedLink("Clear all",!m.busy) { m.filter=HomeFilter() }
}
@Composable private fun RenterDetail(m:RenterViewModel) {
    val home=m.selected?:return
    RenterHeading(home.title,home.areaLabel+" · "+home.propertyType.lowercase().replace('_',' '))
    var photo by remember(home.id) { mutableIntStateOf(0) }
    m.photos.getOrNull(photo)?.let { RemoteHomePhoto(it.signedReadUrl) }
    if(m.photos.size>1) SharedLink("Next photo · "+(photo+1)+" / "+m.photos.size,!m.busy) { photo=(photo+1)%m.photos.size }
    SharedText(kes(home.monthlyRentKes)+" / month"); Gap(12); SharedStatus("Verified "+home.source.lowercase().replaceFirstChar { it.uppercase() }); Gap(12)
    SharedCard { SharedText("The essentials"); home.availableFrom?.let { SharedText("Available from "+it,true) }
        home.bedroomCount?.let { SharedText(it.toString()+" bedrooms · "+home.bathroomCount+" bathrooms",true) }
        SharedText(if(home.furnished) "Furnished" else "Unfurnished",true); SharedText(home.description) }
    RenterAreaMap(LatLng(home.approximateLatitude.toDouble(),home.approximateLongitude.toDouble()))
    SharedText("Approximate area only. No address or exact pin is shown.",true); Gap(12)
    SharedLink("Save home",!m.busy) { m.save(false) }; SharedLink("Remove from saved",!m.busy) { m.save(true) }
    listOf("EXACT_LOCATION_UNLOCK" to "Exact location","VIDEO_UNLOCK" to "Video tour","PHONE_UNLOCK" to "Host phone").forEach { (type,label)->
        SharedLink(label,!m.busy) { m.openAccess(type) }
    }
    SharedButton("Apply for this home",!m.busy,m::beginApply)
}
@Composable private fun RenterApplicationDetail(m:RenterViewModel) {
    val a=m.application?:return
    RenterHeading(if(a.value.status=="APPROVED") "You’re approved" else "Your application",a.source.lowercase().replaceFirstChar { it.uppercase() }+" home")
    SharedCard { SharedStatus(a.value.status); SharedText("Submitted · "+a.value.createdAt,true); SharedText("Updated · "+a.value.updatedAt,true)
        a.value.rejectionReason?.let { SharedText(it) } }
    if(a.value.status=="APPROVED") SharedButton("Continue to booking",!m.busy,m::book)
    if(a.value.status in listOf("SUBMITTED","UNDER_REVIEW","APPROVED")) {
        var withdraw by remember { mutableStateOf(false) }
        SharedLink("Withdraw application",!m.busy) { withdraw=true }
        if(withdraw) AlertDialog(onDismissRequest={withdraw=false},title={Text("Withdraw application?")},text={Text("This does not create a refund.")},
            confirmButton={TextButton({withdraw=false;m.withdraw()}) {Text("Confirm withdrawal")}},dismissButton={TextButton({withdraw=false}) {Text("Keep application")}})
    }
}
@Composable private fun RenterBookingDetail(m:RenterViewModel) {
    val b=m.booking?:return
    RenterHeading(if(b.status=="PAID_HELD") "Payment held" else if(b.status=="ACTIVE") "Your payment was released" else "Review your booking","Booking · "+b.id.take(8))
    MockNotice()
    SharedCard { SharedStatus(b.status); SharedText("Rent · "+minor(b.rentAmountMinor)); SharedText("Deposit · "+minor(b.depositAmountMinor))
        SharedText("Makao Hub fee · "+minor(b.commissionAmountMinor)); SharedText("Total · "+minor(b.totalAmountMinor)) }
    if(b.status in listOf("AWAITING_PAYMENT","PAYMENT_PENDING")) SharedButton("Continue to TEST checkout",!m.busy,m::bookingCheckout)
    if(b.status=="PAID_HELD" && b.renterAgreedAt==null) {
        var agree by remember { mutableStateOf(false) }
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(agree,{agree=it},enabled=!m.busy);SharedText("I agree to these booking terms") }
        SharedButton("Confirm booking agreement",!m.busy && agree,m::agree)
    }
    if(b.status=="PAID_HELD" && b.ownerAgreedAt==null) SharedText("Owner agreement pending",true)
    if(b.status=="PAID_HELD" && b.moveInConfirmedAt==null) {
        var confirm by remember { mutableStateOf(false) }
        SharedButton("Confirm move-in",!m.busy) { confirm=true }
        if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Have you moved in?")},text={Text("Confirm only after you have access to the home.")},
            confirmButton={TextButton({confirm=false;m.moveIn()}) {Text("Yes, confirm move-in")}},dismissButton={TextButton({confirm=false}) {Text("Not yet")}})
    }
    b.releaseEligibleAt?.let { SharedText("Release eligible after · "+it,true) }
    b.releasedAt?.let { SharedText("Released · "+it,true) }
    SharedLink("Refresh booking",!m.busy,m::refreshBooking)
    if(b.status=="PAID_HELD") SharedNote("Disputes are not available in this test build. Move-in confirmation does not itself release money.")
}
@Composable private fun RenterCheckout(m:RenterViewModel) {
    RenterHeading(if(m.screen==RenterScreen.CHECKOUT) "Access checkout" else "Payment status",m.unlockType.lowercase().replace('_',' '))
    MockNotice()
    if(m.screen==RenterScreen.CHECKOUT) {
        val amount=if(m.unlockType=="BOOKING") m.booking?.totalAmountMinor else m.price?.amountMinor
        amount?.let { SharedCard { SharedText("Total · "+minor(it)); SharedText("Mock payment · No real money moves.",true) } }
        SharedButton("Create TEST payment",!m.busy && amount!=null && BuildConfig.MOCK_PAYMENTS_ENABLED,m::createPayment)
    } else m.payment?.let { p->
        SharedCard { SharedStatus(p.status); SharedText(minor(p.amountMinor)); SharedText("Reference · "+p.id.take(8),true) }
        if(p.status=="PENDING") SharedButton("Simulate successful TEST payment",!m.busy && BuildConfig.MOCK_PAYMENTS_ENABLED,m::simulatePayment)
        SharedLink("Refresh test status",!m.busy,m::refreshPayment)
        if(p.status=="SUCCEEDED") {
            if(p.paymentType=="BOOKING") SharedButton("View booking",!m.busy) { m.go(RenterScreen.BOOKING) }
            else SharedButton("View unlocked access",!m.busy,m::viewPaidAccess)
        }
        if(p.status=="FAILED") SharedNote("No access was granted. Return to the listing to start a new test attempt.")
    }
}
@Composable private fun RenterAccess(m:RenterViewModel) {
    RenterHeading(when(m.unlockType) {"PHONE_UNLOCK"->"Your unlocked contact";"VIDEO_UNLOCK"->"Your video tour";else->"Your unlocked location"},"Server-authorised access")
    m.location?.let { RenterAreaMap(LatLng(it.latitude.toDouble(),it.longitude.toDouble()),exact=true); SharedText(it.areaLabel) }
    m.phone?.let { SharedCard { SharedText(it.ownerName); SharedText(it.phoneNumber) } }
    m.videos.forEach { ProtectedVideo(it.signedReadUrl) }
    if(m.unlockType=="VIDEO_UNLOCK" && m.videos.isEmpty()) SharedText("No video is available.",true)
    SharedNote("Location, video and phone use separate access checks. Contact details remain masked in chat.")
}
@Composable private fun RenterVerification(m:RenterViewModel) {
    var camera by remember { mutableStateOf<RenterEvidence?>(null) }
    var choosing by remember { mutableStateOf<RenterEvidence?>(null) }
    var correcting by remember { mutableStateOf(false) }
    val resolver=LocalContext.current.contentResolver
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->val type=choosing;choosing=null;if(uri!=null && type!=null) m.upload(type,resolver,uri) }
    val status=m.verification?.status
    RenterHeading(when(status) { "PENDING"->"Documents Under Review";"VERIFIED"->"Verification Approved!";"REJECTED"->"Verification Rejected";else->"Verify your identity" },"Renter")
    when {
        status in listOf("PENDING","VERIFIED") -> {
            SharedCard { SharedText(if(status=="PENDING") "Your ID and selfie are under review." else "Your Renter role is verified.") }
            SharedButton("Close & Back to Main",!m.busy) { m.go(RenterScreen.HOME) }
            SharedLink("Refresh status",!m.busy,m::refreshVerification)
        }
        status=="REJECTED" && !correcting -> {
            SharedCard { SharedText(m.verification?.rejectionReason?:"Please contact support for the required corrections.") }
            SharedButton("Re-submit documents",!m.busy) { correcting=true }
        }
        camera!=null -> {
            val type=camera!!
            EvidenceCamera(type==RenterEvidence.SELFIE,!m.busy,{bitmap->camera=null;m.capture(type,bitmap)},m::say)
            SharedLink("Cancel camera",!m.busy) { camera=null }
        }
        else -> {
            RenterEvidence.entries.forEach { type->SharedCard {
                SharedText(type.title); SharedText(if(renterEvidenceReady(type,m.documents)) "Uploaded" else "Required",true)
                SharedLink(if(type==RenterEvidence.SELFIE) "Capture Live Photo" else "Take ID Photo",!m.busy) { camera=type }
                if(type!=RenterEvidence.SELFIE) SharedLink("Choose from gallery",!m.busy) { choosing=type;picker.launch(arrayOf("image/jpeg","image/png")) }
            } }
            if(m.pendingEvidence!=null) SharedButton("Retry current document",!m.busy,m::retryEvidence)
            else {
                m.documents.filter { it.status=="UPLOADING" }.forEach { doc->SharedLink("Confirm "+doc.documentType.lowercase().replace('_',' '),!m.busy) { m.confirmStored(doc.documentId) } }
                SharedButton("Submit Verification",!m.busy && renterEvidenceComplete(m.documents),m::submitEvidence)
            }
            SharedText("ID front and back · Selfie · JPEG or PNG up to 10 MB",true)
        }
    }
}
@Composable private fun RenterHeading(title:String,subtitle:String) { Text(title,color=Brown,fontSize=25.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp);Gap(10);SharedText(subtitle,true);Gap(20) }
@Composable private fun RenterInput(label:String,value:String,enabled:Boolean,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,enabled=enabled,label={Text(label)},singleLine=true,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().padding(bottom=12.dp))
}
@Composable private fun MockNotice() { SharedNote("TEST / MOCK PAYMENT\nNo real money moves.") }
private fun kes(value:Long)="KSh "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(value)
private fun minor(value:Long)="KSh "+BigDecimal.valueOf(value,2).toPlainString()
private val photoClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(20,TimeUnit.SECONDS).build()
internal fun allowedHomeMedia(url:String):Boolean=runCatching { val u=url.toHttpUrl();u.isHttps && u.host.endsWith(".supabase.co") && u.username.isEmpty() && u.password.isEmpty() && u.encodedPath.startsWith("/storage/v1/object/sign/") }.getOrDefault(false)
@Composable private fun RemoteHomePhoto(url:String) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        try { bitmap=withContext(Dispatchers.IO) {
            require(allowedHomeMedia(url))
            photoClient.newCall(Request.Builder().url(url).build()).execute().use { response->
                check(response.isSuccessful)
                val bytes=requireNotNull(response.body).byteStream().use { input->
                    val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                    while(true) { val n=input.read(chunk);if(n<0) break;require(out.size()+n<=2_097_152);out.write(chunk,0,n) }
                    out.toByteArray()
                }
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(validVerificationImageDimensions(bounds.outWidth,bounds.outHeight))
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>700) sample*=2
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample })
            }
        } } catch(e:CancellationException) { throw e } catch(_:Exception) { failed=true }
    }
    bitmap?.let { Image(it.asImageBitmap(),contentDescription="Listing photo",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp))) }
    if(failed) SharedText("Photo unavailable",true)
}
@Composable private fun ProtectedVideo(url:String) {
    if(!allowedHomeMedia(url)) { SharedText("Video unavailable",true);return }
    val context=LocalContext.current
    val player=remember(url) { VideoView(context).apply { setVideoURI(Uri.parse(url));setMediaController(MediaController(context).also { it.setAnchorView(this) }) } }
    DisposableEffect(player) { onDispose { player.stopPlayback() } }
    AndroidView(factory={player},modifier=Modifier.fillMaxWidth().height(230.dp))
}
