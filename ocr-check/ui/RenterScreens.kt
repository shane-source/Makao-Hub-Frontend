@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.makaohub.android.ui

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

@Composable fun OfflineRenterDashboard(auth: AuthViewModel) {
    val homes = remember { auth.offlineHomes?.read().orEmpty() }
    var search by rememberSaveable { mutableStateOf("") }
    val visibleHomes = homes.filter { search.isBlank() || it.title.contains(search, true) || it.area.contains(search, true) }
    Column(Modifier.fillMaxSize().background(DashboardBackground).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text("makao hub", color = Brown, fontWeight = FontWeight.Bold)
        Gap(24)
        RenterHeading("Find your next chapter", "Browse homes you loaded earlier.")
        SharedNote("Makao Hub is offline. These homes may have changed or may no longer be available. Reconnect to use account features; sign in again if your session has expired.")
        auth.message?.let { SharedText(it, true) }
        Gap(12)
        SharedButton(if (auth.busy) "Checking connection…" else "Try again", !auth.busy, auth::retryOffline)
        if (!auth.offlineSession) SharedLink("Sign in", !auth.busy) { auth.navigate(Page.LOGIN) }
        Gap(12)
        OutlinedTextField(value = search, onValueChange = { search = it.take(150) }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Search saved homes or areas") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }, shape = RoundedCornerShape(28.dp))
        Gap(16)
        if (homes.isEmpty()) SharedCard { SharedText("No previously loaded homes are available on this device.") }
        else if (visibleHomes.isEmpty()) SharedCard { SharedText("No saved homes match your search.") }
        visibleHomes.forEach { home ->
            SharedCard {
                SharedText(home.title)
                SharedText(home.area, true)
                SharedText(kes(home.rent) + " / month")
                SharedText((home.bedrooms?.let { "$it beds · " } ?: "") + "${home.bathrooms} baths", true)
            }
            Gap(12)
        }
    }
}

@Composable fun RenterWorkspace(auth:AuthViewModel,profile:MyProfile) {
    val model:RenterViewModel=viewModel(key="renter-"+profile.id,factory=object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T =
            RenterViewModel(RenterRepository(auth.repository, auth.offlineHomes)) { auth.navigate(Page.LOGIN) } as T
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
    LaunchedEffect(model.offline) { if (model.offline) { model.consumeOffline(); auth.enterOffline() } }
    val back={ model.go(when(model.screen) {
        RenterScreen.DETAIL,RenterScreen.FILTERS,RenterScreen.AREA -> RenterScreen.RESULTS
        RenterScreen.REPORT -> RenterScreen.DETAIL
        RenterScreen.APPLY,RenterScreen.ACCESS,RenterScreen.UNLOCK_INTRO,RenterScreen.CHECKOUT -> RenterScreen.DETAIL
        RenterScreen.PAYMENT -> if(model.unlockType=="BOOKING") RenterScreen.BOOKING else RenterScreen.CHECKOUT
        RenterScreen.APPLICATION -> RenterScreen.APPLICATIONS
        RenterScreen.CHAT -> RenterScreen.INBOX
        else -> RenterScreen.HOME
    }) }
    BackHandler(model.screen!=RenterScreen.HOME,onBack=back)
    if(model.screen==RenterScreen.VERIFICATION) { RenterVerification(model); return }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(model.notice) { model.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() } }
    Scaffold(containerColor=if(model.screen in listOf(RenterScreen.HOME,RenterScreen.RESULTS,RenterScreen.SAVED,RenterScreen.PROFILE,RenterScreen.INBOX)) DashboardBackground else Cream,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(model.screen!=RenterScreen.VERIFICATION) Box(Modifier.navigationBarsPadding().padding(horizontal=24.dp).padding(bottom=16.dp,top=8.dp)) {
        Surface(color=androidx.compose.ui.graphics.Color.White,shape=RoundedCornerShape(18.dp)) { FixedFontScale { NavigationBar(containerColor=androidx.compose.ui.graphics.Color.White,tonalElevation=0.dp) {
            listOf(Triple(RenterScreen.HOME,"Home",Icons.Outlined.Home),Triple(RenterScreen.SAVED,"Saved",Icons.Outlined.FavoriteBorder),
                Triple(RenterScreen.APPLICATIONS,"Applications",Icons.Outlined.Description),Triple(RenterScreen.INBOX,"Messages",Icons.Outlined.ChatBubbleOutline),
                Triple(RenterScreen.PROFILE,"Profile",Icons.Outlined.PersonOutline)).forEach { (page,label,icon)->
                val selected=when(page) {
                    RenterScreen.HOME -> model.screen in listOf(RenterScreen.HOME,RenterScreen.RESULTS,RenterScreen.AREA,RenterScreen.FILTERS,RenterScreen.DETAIL)
                    RenterScreen.APPLICATIONS -> model.screen in listOf(RenterScreen.APPLICATIONS,RenterScreen.APPLICATION,RenterScreen.BOOKING)
                    RenterScreen.INBOX -> model.screen in listOf(RenterScreen.INBOX,RenterScreen.CHAT)
                    RenterScreen.PROFILE -> model.screen in listOf(RenterScreen.PROFILE,RenterScreen.HISTORY)
                    else -> model.screen==page
                }
                NavigationBarItem(selected=selected,onClick={model.go(page)},enabled=!model.busy,
                    colors=NavigationBarItemDefaults.colors(indicatorColor=androidx.compose.ui.graphics.Color.Transparent,selectedIconColor=Brown,selectedTextColor=Brown,unselectedIconColor=Muted,unselectedTextColor=Muted),
                    icon={BadgedTabIcon(icon,when(page) { RenterScreen.APPLICATIONS->ApplicationSeen.unseen(androidx.compose.ui.platform.LocalContext.current,model.applications.map { "${it.value.id}:${it.value.status}" })
                        RenterScreen.INBOX->ChatSeen.unread(androidx.compose.ui.platform.LocalContext.current,model.threads.map { it.id to it.lastMessageAt })
                        else->0 },LocalContentColor.current)},label={NavLabel(label)})
            }
        } } } }
    }) { insets->
        val scroll=rememberScrollState()
        LaunchedEffect(model.screen) { scroll.scrollTo(0) }
        LaunchedEffect(model) { while(true) { model.refreshBadges(); delay(30_000) } }
        val seenContext=androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(model.screen,model.applications) {
            if (model.screen in listOf(RenterScreen.APPLICATIONS,RenterScreen.APPLICATION,RenterScreen.BOOKING))
                ApplicationSeen.markSeen(seenContext,model.applications.map { "${it.value.id}:${it.value.status}" })
        }
        Column(Modifier.fillMaxSize().padding(insets).statusBarsPadding().imePadding().verticalScroll(scroll).padding(horizontal=22.dp).padding(bottom=24.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(back,enabled=!model.busy,modifier=Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",tint=Brown,modifier=Modifier.size(18.dp)) }
                Spacer(Modifier.width(6.dp)); Text("makao hub",color=Brown,fontSize=15.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton({ model.go(RenterScreen.HISTORY) }, enabled = !model.busy, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Payments, "Payments", tint = Brown, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(56.dp))
            }
            if(model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Gap(12) }
            when(model.screen) {
                RenterScreen.HOME,RenterScreen.RESULTS,RenterScreen.SAVED -> RenterHomes(model,profile)
                RenterScreen.AREA,RenterScreen.FILTERS -> RenterFilters(model)
                RenterScreen.DETAIL -> RenterDetail(model)
                RenterScreen.REPORT -> RenterReport(model)
                RenterScreen.VERIFICATION -> RenterVerification(model)
                RenterScreen.APPLY -> {
                    RenterHeading("Ready to book?",model.selected?.title?:"")
                    SharedCard { SharedText("Your application"); SharedText(profile.fullName)
                        model.selected?.let { SharedText(kes(it.monthlyRentKes)+" / month") } }
                    var consent by rememberSaveable { mutableStateOf(false) }
                    var moveIn by rememberSaveable { mutableStateOf<String?>(null) }
                    var note by rememberSaveable { mutableStateOf("") }
                    var picking by remember { mutableStateOf(false) }
                    val today=remember { java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi")) }
                    val chosen=moveIn?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                    val dateOk=chosen!=null && !chosen.isBefore(today) && !chosen.isAfter(today.plusYears(1))
                    SharedCard {
                        Text("When do you want to move in?",color=Brown,fontSize=18.sp)
                        OutlinedButton({ picking=true },enabled=!model.busy,modifier=Modifier.fillMaxWidth()) {
                            Text(moveInLabel(moveIn)?:"Choose move-in date",color=Brown)
                        }
                        if(chosen!=null && !dateOk) SharedText("Choose a date from today up to one year ahead.",true)
                    }
                    if(picking) {
                        val pickerState=rememberDatePickerState()
                        DatePickerDialog(onDismissRequest={picking=false},
                            confirmButton={ TextButton({
                                pickerState.selectedDateMillis?.let { moveIn=java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                                picking=false
                            }) { Text("Select") } },
                            dismissButton={ TextButton({ picking=false }) { Text("Cancel") } }) { DatePicker(pickerState) }
                    }
                    OutlinedTextField(note,{ note=it.take(500) },label={ Text("Message to the host (optional)") },minLines=3,maxLines=5,
                        enabled=!model.busy,modifier=Modifier.fillMaxWidth())
                    Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(consent,{consent=it},enabled=!model.busy); SharedText("I consent to submit this application") }
                    SharedButton("Submit booking request",!model.busy && consent && dateOk) { model.apply(moveIn.orEmpty(),note) }
                }
                RenterScreen.APPLICATIONS -> {
                    RenterHeading("Your applications","Keep track of every next step.")
                    AppSummaryRow(model.applications.map { it.value.status })
                    model.applications.forEach { a->AppAccentCard(a.value.status) {
                        var cover by remember(a.value.id) { mutableStateOf<String?>(null) }
                        LaunchedEffect(a.value.id) { cover=try { model.applicationCover(a) } catch(e:CancellationException) { throw e } catch(_:Exception) { null } }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            val thumb=Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))
                            cover?.let { RemoteHomePhoto(it,thumb) } ?: Box(thumb.background(Sand),contentAlignment=Alignment.Center) { Icon(Icons.Outlined.Home,null,tint=Brown,modifier=Modifier.size(32.dp)) }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(a.source.lowercase().replaceFirstChar { it.uppercase() }+" home",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                                AppStatusBadge(a.value.status)
                                Text(appStatusLook(a.value.status).hint,color=Muted,fontSize=12.sp)
                            }
                        }
                        Button(onClick={ model.openApplication(a) },enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=46.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Brown)) {
                            Text("View application",color=Color.White,fontWeight=FontWeight.SemiBold)
                        }
                    } }
                    if(model.applications.isEmpty()) SharedText("No applications yet.",true)
                }
                RenterScreen.APPLICATION -> RenterApplicationDetail(model)
                RenterScreen.BOOKING -> RenterBookingDetail(model)
                RenterScreen.UNLOCK_INTRO -> RenterUnlockIntro(model)
                RenterScreen.CHECKOUT,RenterScreen.PAYMENT -> RenterCheckout(model)
                RenterScreen.ACCESS -> RenterAccess(model)
                RenterScreen.HISTORY -> {
                    RenterHeading("Payment history","Your payments")
                    model.history?.items?.forEach { p->SharedCard { SharedText(paymentTitle(p.paymentType)); RenterStatus(paymentStatus(p)); SharedText(minor(p.amountMinor))
                        SharedLink("View",!model.busy) { model.openPayment(p) } } }
                    if(model.history?.hasNext==true) SharedLink("Load more",!model.busy,model::nextHistory)
                }
                RenterScreen.INBOX -> {
                    var query by rememberSaveable { mutableStateOf("") }
                    ProtectedInboxHeader(query) { query=it }
                    val visible=model.threads.filter { t->
                        query.isBlank() || listOfNotNull(t.name,t.lastMessage).any { it.contains(query.trim(),ignoreCase=true) }
                    }
                    visible.forEach { t->
                        val landlord=t.source=="LANDLORD"
                        ProtectedConversationRow(
                            title=t.name ?: if(landlord) "Landlord" else "Sublessor",
                            detail=t.lastMessage ?: "Start a conversation",
                            status=t.lastMessageAt?.let { kenyaTime(it, "d MMM yyyy") } ?: "",
                            enabled=!model.busy,avatarUrl=t.avatar
                        ) { model.openThread(t) }
                    }
                    if(visible.isEmpty()) SharedText(if(query.isBlank()) "No conversations yet." else "No conversations match your search.",true)
                    if(model.moreThreads) SharedLink("Load more",!model.busy,model::nextThreads)
                }
                RenterScreen.CHAT -> {
                    val selectedThread=model.thread ?: return@Column
                    val landlord=selectedThread.source=="LANDLORD"
                    ProtectedConversation(
                        title=selectedThread.name ?: if(landlord) "Landlord" else "Sublessor",
                        roleLabel=if(landlord) "Landlord" else "Sublessor",
                        conversationType=if(landlord) "Rental conversation" else "Roommate conversation",
                        contextLabel=if(landlord) "Rental enquiry" else "Roommate enquiry",
                        threadId=selectedThread.id,
                        messages=model.messages?.items.orEmpty(),
                        ownUserId=profile.id,
                        connected=model.chatReady,
                        awaitingEcho=model.awaitingEcho,
                        threadOpen=selectedThread.status=="OPEN",
                        draft=model.chatDraft,
                        busy=model.busy,
                        hasOlder=model.messages?.hasNext==true,
                        onOlder=model::olderMessages,
                        onReconnect=model::reconnect,
                        onAcknowledge=model::acknowledgeMessages,
                        onDraft={ model.chatDraft=it },
                        onSend=model::send
                    )
                }
                RenterScreen.PROFILE -> {
                    AccountProfile(profile, model.verification?.status ?: "NOT_STARTED", model.busy, auth,
                        documents = model.documents,
                        onRefreshDocuments = model::refreshVerification,
                        actions = listOf(
                            ProfileAction("Saved homes", Icons.Outlined.FavoriteBorder) { model.go(RenterScreen.SAVED) },
                            ProfileAction("My applications", Icons.Outlined.Description) { model.go(RenterScreen.APPLICATIONS) },
                            ProfileAction("Payment history", Icons.Outlined.Payments) { model.go(RenterScreen.HISTORY) }
                        ),
                        onVerification = { model.go(RenterScreen.VERIFICATION) },
                        onLogout = { model.clear(); auth.logout() })
                }
            }
        }
    }
}

@Composable private fun RenterHomes(m:RenterViewModel,p:MyProfile) {
    RenterHeading(when(m.screen) { RenterScreen.HOME->"Find your next chapter"; RenterScreen.SAVED->"Homes you’re saving"; else->"Homes around "+m.filter.area.ifBlank { "you" } },
        if(m.screen==RenterScreen.HOME) "Karibu, "+p.fullName.substringBefore(' ')+". Let’s find somewhere that feels right." else "Whole homes and shared spaces")
    SharedStatus(if(m.verification?.status=="VERIFIED") "Verified Renter" else m.verification?.status?:"NOT_STARTED"); Gap(12)
    val keyboard=LocalSoftwareKeyboardController.current
    OutlinedTextField(value=m.filter.area,onValueChange={m.filter=m.filter.copy(area=it.take(150))},
        modifier=Modifier.fillMaxWidth(),enabled=!m.busy,singleLine=true,
        placeholder={Text("Search an area",color=Muted)},
        leadingIcon={Icon(Icons.Outlined.Search,contentDescription="Search",tint=Brown)},
        trailingIcon={if(m.filter.area.isNotBlank()) IconButton(onClick={m.filter=m.filter.copy(area="");m.search();keyboard?.hide()}) {
            Icon(Icons.Outlined.Close,contentDescription="Clear area search",tint=Muted) }},
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
        keyboardActions=KeyboardActions(onSearch={keyboard?.hide();m.search()}),
        shape=RoundedCornerShape(28.dp),colors=OutlinedTextFieldDefaults.colors(
            focusedContainerColor=Color.White,unfocusedContainerColor=Color.White,focusedBorderColor=Brown,unfocusedBorderColor=Border))
    Gap(8)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(m.filter.max.takeIf { it.isNotBlank() }?.let { "Up to KSh $it" }?:"All budgets",color=Muted,fontSize=12.sp)
        TextButton(onClick={keyboard?.hide();m.search()},enabled=!m.busy) { Text("Search",color=Brown) }
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        SharedLink("Filters",!m.busy) { m.go(RenterScreen.FILTERS) }
        SharedLink("Relevant",!m.busy) { m.say("Sponsored listings appear first, followed by recently updated homes.") }
        SharedLink("Map view",!m.busy) { m.say("Public map browsing is not available yet. Approximate areas remain private-safe.") }
    }
    Row { listOf(null to "All homes","LANDLORD" to "Whole homes","SUBLESSOR" to "Shared spaces").forEach { (source,label)->
        FilterChip(m.filter.source==source,{m.filter=m.filter.copy(source=source);m.search()},label={Text(label,fontSize=10.sp)},enabled=!m.busy) } }
    m.homes?.items?.forEach { home->RenterHomeCard(home,m.isSaved(home),m.busy,{m.open(home)},{m.save(home,m.isSaved(home))},m::cover) }
    if(!m.busy && m.homes?.items?.isEmpty()==true) SharedCard { SharedText("No homes match yet."); SharedText("Try a nearby area or a wider rent range. Your saved filters are safe.",true); SharedLink("Clear filters",true) { m.filter=HomeFilter();m.search() } }
    if(m.homes?.hasNext==true) SharedLink("Next page",!m.busy,m::nextHomes)
    if(m.screen==RenterScreen.HOME) SharedLink("Saved homes · View all",!m.busy) { m.go(RenterScreen.SAVED) }
}

@Composable private fun RenterHomeCard(home:RenterHome,saved:Boolean,busy:Boolean,onOpen:()->Unit,onSave:()->Unit,cover:suspend (RenterHome)->HomeMedia?) {
    val photo by produceState<HomeMedia?>(null,home.id,home.source) { value=try { cover(home) } catch(e:CancellationException) { throw e } catch(_:Exception) { null } }
    Surface(onClick=onOpen,enabled=!busy,modifier=Modifier.fillMaxWidth(),color=Color.White,
        shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Border)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)).background(Sand),contentAlignment=Alignment.Center) {
                if(photo!=null) RemoteHomePhoto(photo!!.signedReadUrl)
                else Icon(Icons.Outlined.Home,null,tint=Muted,modifier=Modifier.size(36.dp))
                Surface(shape=RoundedCornerShape(14.dp),color=Color.White.copy(alpha=0.94f),modifier=Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    IconButton(onClick=onSave,enabled=!busy) { Icon(if(saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription=if(saved) "Remove from saved homes" else "Save home",tint=Brown) }
                }
            }
            if(home.sponsored) Text("Boosted · Sponsored",color=Brown,fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(home.title,color=Ink,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                Surface(color=Color(0xFFF1E8E3),shape=RoundedCornerShape(7.dp)) {
                    Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check,null,Modifier.size(14.dp),tint=Brown);Spacer(Modifier.width(4.dp))
                        Text("VERIFIED ${home.source.uppercase()}",fontSize=9.sp,color=Ink)
                    }
                }
            }
            val rooms=(home.bedroomCount?.let { " · $it beds" }?:"")+" · ${home.bathroomCount} baths"
            Text(home.areaLabel+rooms,color=Muted,fontSize=13.sp)
            Text(kes(home.monthlyRentKes)+"/mo",color=Brown,fontSize=21.sp,fontWeight=FontWeight.Bold)
        }
    }
    Gap(16)
}
@Composable private fun RenterFilters(m:RenterViewModel) {
    RenterHeading(if(m.screen==RenterScreen.AREA) "Where would you like to live?" else "Make it your kind of home","Filter results · choose what matters to you")
    RenterInput("Search area",m.filter.area,!m.busy) { m.filter=m.filter.copy(area=it) }
    if(m.screen==RenterScreen.AREA) listOf("Nairobi","Rongai","Thika Road","Mombasa").forEach { area->
        SharedLink(area,!m.busy) { m.filter=m.filter.copy(area=area) }
    } else {
        RenterInput("Minimum rent · KSh",m.filter.min,!m.busy) { m.filter=m.filter.copy(min=it) }
        RenterInput("Maximum rent · KSh",m.filter.max,!m.busy) { m.filter=m.filter.copy(max=it) }
        ListingCountDropdown("Bedrooms · minimum",m.filter.bedrooms,0..20,!m.busy,allowAny=true) { m.filter=m.filter.copy(bedrooms=it) }
        var types by remember { mutableStateOf(false) }
        Box { SharedLink(m.filter.propertyType?.lowercase()?.replace('_',' ')?.replaceFirstChar { it.uppercase() }?:"Any property type",!m.busy) { types=true }
            DropdownMenu(types,{types=false}) {
                DropdownMenuItem(text={Text("Any property type")},onClick={m.filter=m.filter.copy(propertyType=null);types=false})
                HomeFilter.PROPERTY_TYPES.sorted().forEach { type->DropdownMenuItem(text={Text(type.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() })},onClick={m.filter=m.filter.copy(propertyType=type);types=false}) }
            }
        }
        FilterToggle("Furnished",m.filter.furnished==true,!m.busy) { m.filter=m.filter.copy(furnished=if(it) true else null) }
        RenterInput("Available by · YYYY-MM-DD",m.filter.availableBy,!m.busy) { m.filter=m.filter.copy(availableBy=it) }
        listOf(null to "Both listing sources","LANDLORD" to "Landlord","SUBLESSOR" to "Sublessor").forEach { (source,title)->
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(m.filter.source==source,{m.filter=m.filter.copy(source=source)},enabled=!m.busy); SharedText(title) }
        }
    }
    SharedButton(if(m.screen==RenterScreen.AREA) "Use this area" else "Apply filters",!m.busy,m::search)
    SharedLink("Clear all",!m.busy) { m.filter=HomeFilter() }
}
@Composable private fun DetailChip(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,modifier:Modifier=Modifier) {
    Surface(color=Color(0xFFFBF3E8),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFFEBD9BF)),modifier=modifier) {
        Column(Modifier.padding(horizontal=8.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(icon,null,tint=Brown,modifier=Modifier.size(26.dp)); Gap(6)
            Text(label,color=Ink,fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
@Composable private fun AccessTile(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,sub:String,enabled:Boolean,unlocked:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,color=Color.White.copy(alpha=0.14f),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=0.28f)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(color=Color(0xFFF5C26B),shape=CircleShape) { Icon(icon,null,tint=Brown,modifier=Modifier.padding(11.dp).size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(if(unlocked) "Unlocked · tap to view" else sub,color=Color.White.copy(alpha=0.78f),fontSize=12.sp,lineHeight=16.sp)
            }
            if(unlocked) Icon(Icons.Outlined.CheckCircle,"Unlocked",tint=Color(0xFF7BD389),modifier=Modifier.size(24.dp))
            else Icon(Icons.Outlined.Lock,null,tint=Color.White.copy(alpha=0.85f),modifier=Modifier.size(18.dp))
        }
    }
}
@Composable private fun RenterDetail(m:RenterViewModel) {
    val home=m.selected?:return
    val context=LocalContext.current
    var photo by remember(home.id) { mutableIntStateOf(0) }
    val saved=m.isSaved(home)
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)).background(Sand)) {
        m.photos.getOrNull(photo)?.let { RemoteHomePhoto(it.signedReadUrl, Modifier.fillMaxSize()) }
        if(m.photos.isEmpty()) Text("No photos available",Modifier.align(Alignment.Center),color=Muted)
        Box(Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent,Color(0xCC1E0F05)))))
        Surface(color=Color(0xFF417653),shape=RoundedCornerShape(20.dp),modifier=Modifier.align(Alignment.TopStart).padding(14.dp)) {
            Row(Modifier.padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.VerifiedUser,null,tint=Color.White,modifier=Modifier.size(16.dp))
                Text("Verified ${home.source.lowercase().replaceFirstChar { it.uppercase() }}",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            }
        }
        Surface(onClick={
            val message="${home.title} · ${home.areaLabel} · ${kes(home.monthlyRentKes)} / month on Makao Hub"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,message),"Share listing"))
        },color=Color.White,shape=CircleShape,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Outlined.Share,"Share listing",Modifier.padding(12.dp).size(22.dp),tint=Brown)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start=18.dp,bottom=16.dp)) {
            Text(kes(home.monthlyRentKes),color=Color.White,fontSize=30.sp,fontWeight=FontWeight.ExtraBold)
            Text("per month",color=Color.White.copy(alpha=0.85f),fontSize=13.sp)
        }
        if(m.photos.size>1) {
            Surface(onClick={photo=(photo+1)%m.photos.size},color=Color.White.copy(alpha=0.94f),shape=RoundedCornerShape(20.dp),modifier=Modifier.align(Alignment.BottomEnd).padding(14.dp)) {
                Text("${photo+1} / ${m.photos.size}  ·  Next",Modifier.padding(horizontal=14.dp,vertical=8.dp),color=Brown,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
    Gap(18)
    Text(home.title,color=Ink,fontSize=28.sp,lineHeight=33.sp,fontWeight=FontWeight.ExtraBold)
    Gap(6)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.LocationOn,null,tint=Brown,modifier=Modifier.size(18.dp))
        Text(home.areaLabel+" · approximate location",color=Muted,fontSize=15.sp)
    }
    Gap(18)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        home.bedroomCount?.let { DetailChip(Icons.Outlined.Bed,"$it bedrooms",Modifier.weight(1f)) }
        home.bathroomCount?.let { DetailChip(Icons.Outlined.Bathtub,"$it bathrooms",Modifier.weight(1f)) }
        DetailChip(Icons.Outlined.Chair,if(home.furnished) "Furnished" else "Unfurnished",Modifier.weight(1f))
    }
    home.availableFrom?.let {
        Gap(10)
        Surface(color=Color(0xFFFFF4D6),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Color(0xFFF1D58A)),modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CalendarMonth,null,tint=Brown)
                Text("Available from $it",color=Ink,fontWeight=FontWeight.SemiBold)
            }
        }
    }
    if(home.description.isNotBlank()) {
        Gap(20)
        Text("About this home",color=Ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Gap(8)
        Text(home.description,color=Ink,fontSize=15.sp,lineHeight=22.sp)
    }
    if (m.listingAmenities.isNotEmpty()) {
        Gap(22)
        Text("Amenities",color=Ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Gap(10)
        m.listingAmenities.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                pair.forEach { a ->
                    Surface(color=Color.White,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border),modifier=Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CheckCircle,null,tint=Color(0xFF417653),modifier=Modifier.size(18.dp))
                            Text(a.name,color=Ink,fontSize=14.sp)
                        }
                    }
                }
                if(pair.size==1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Gap(14)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brown,Color(0xFF9A5A26))))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Lock,null,tint=Color(0xFFF5C26B))
                Column {
                    Text("Secure access",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.ExtraBold)
                    Text("Unlock only what you need. Pay once per item.",color=Color.White.copy(alpha=0.8f),fontSize=13.sp)
                }
            }
            Gap(2)
            AccessTile(Icons.Outlined.Videocam,"Full video tour","Walk through every room before you visit",!m.busy,m.isUnlocked("VIDEO_UNLOCK")) { m.openAccess("VIDEO_UNLOCK") }
            AccessTile(Icons.Outlined.Call,"Host contact","Get the owner's phone number",!m.busy,m.isUnlocked("PHONE_UNLOCK")) { m.openAccess("PHONE_UNLOCK") }
            AccessTile(Icons.Outlined.LocationOn,"Exact location","See the precise address and map pin",!m.busy,m.isUnlocked("EXACT_LOCATION_UNLOCK")) { m.openAccess("EXACT_LOCATION_UNLOCK") }
        }
    }
    Gap(22)
    Text("Where it is",color=Ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
    Gap(10)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp,Border,RoundedCornerShape(20.dp))) {
        RenterAreaMap(LatLng(home.approximateLatitude.toDouble(),home.approximateLongitude.toDouble()))
    }
    Gap(6)
    SharedText("Approximate area only. No address or exact pin is shown.",true); Gap(14)
    SharedButton("Start booking",!m.busy,m::beginApply)
    Gap(4)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick={ m.save(saved) },enabled=!m.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Brown)) {
            Icon(if(saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,null,tint=Brown,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(if(saved) "Saved" else "Save home",color=Brown)
        }
        OutlinedButton(onClick=m::openReport,enabled=!m.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)) {
            Icon(Icons.Outlined.Flag,null,tint=Muted,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text("Report",color=Muted)
        }
    }
}
@Composable private fun FilterToggle(label:String,selected:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    Surface(onClick={onChange(!selected)},enabled=enabled,color=if(selected) Sand else androidx.compose.ui.graphics.Color.White,
        shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(if(selected) 2.dp else 1.dp,if(selected) Brown else Border),modifier=Modifier.fillMaxWidth().padding(bottom=10.dp)) {
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected,onClick=null,enabled=enabled); Spacer(Modifier.width(8.dp));SharedText(label)
        }
    }
}
@Composable private fun RenterUnlockIntro(m:RenterViewModel) {
    val home=m.selected?:return
    val location=m.unlockType=="EXACT_LOCATION_UNLOCK"
    val video=m.unlockType=="VIDEO_UNLOCK"
    val title=when { location->"Get to know the neighbourhood";video->"Take a closer look";else->"Contact is protected" }
    HeroBanner(Brown,if(location) Icons.Outlined.LocationOn else if(video) Icons.Outlined.Videocam else Icons.Outlined.Call,title,
        when { location->"Unlock the exact location of ${home.title}.";video->"${home.title} · video tour locked";else->"Host contact · masked until you unlock this listing." })
    if(video) m.photos.firstOrNull()?.let { RemoteHomePhoto(it.signedReadUrl,Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp))); Gap(12) }
    else { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) { RenterAreaMap(LatLng(home.approximateLatitude.toDouble(),home.approximateLongitude.toDouble())) }; Gap(8) }
    SharedText(if(location) "Approximate area only. No address or coordinates are shown." else if(video) "A listing video is available only after a paid video entitlement." else "Phone access is shown only in its dedicated view. Chats continue to mask contact details.",true)
    Gap(12)
    HighlightCard {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(color=Color(0xFFF5C26B),shape=CircleShape) { Icon(Icons.Outlined.LockOpen,null,tint=Brown,modifier=Modifier.padding(9.dp).size(20.dp)) }
            Text(if(location) "What you’ll unlock" else if(video) "Inside the home" else "Host contact",color=Ink,fontSize=19.sp,fontWeight=FontWeight.ExtraBold)
        }
        SharedText(if(location) "Exact map pin\nAvailable only after a Paid location entitlement." else if(video) "Video access\nUnlocked only for this listing after confirmed payment." else "Phone number\nShown only after a Paid phone entitlement for this listing.")
    }
    val amount=m.price?.amountMinor
    SharedButton("Continue"+(amount?.let { " · "+minor(it) }?:""),!m.busy && amount!=null,m::continueToCheckout)
    SharedLink("Not now",!m.busy) { m.go(RenterScreen.DETAIL) }
}
@Composable private fun RenterReport(m:RenterViewModel) {
    val home=m.selected?:return
    RenterHeading("Report this listing",home.title+" · "+home.areaLabel)
    val labels=mapOf("MISLEADING_CONTENT" to "Misleading photos or description","PAYMENT_REQUEST" to "Suspicious request for money",
        "UNSAFE_CONTENT" to "Unsafe or inappropriate content","OTHER" to "Other concern")
    labels.forEach { (value,label)->
        Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(m.reportCategory==value,{m.reportCategory=value},enabled=!m.busy);SharedText(label) }
    }
    RenterInput("Tell us more",m.reportDescription,!m.busy,4) { m.reportDescription=it.take(1000) }
    SharedNote("Your report is private. Please don’t include ID numbers or contact details.")
    SharedButton("Submit report",!m.busy && m.reportDescription.isNotBlank(),m::submitReport)
}
@Composable private fun StepDot(label:String,state:Int,modifier:Modifier=Modifier) {
    // state: 0 upcoming, 1 current, 2 done, 3 declined
    val color=when(state) { 2->Color(0xFF417653); 1->Color(0xFFA56C10); 3->Color(0xFFB24D45); else->Color(0xFFD9CFC2) }
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(color=if(state==0) Color.White else color,shape=CircleShape,border=BorderStroke(2.dp,color)) {
            Icon(when(state) { 2->Icons.Outlined.Check; 3->Icons.Outlined.Close; 1->Icons.Outlined.HourglassTop; else->Icons.Outlined.Circle },null,
                tint=if(state==0) color else Color.White,modifier=Modifier.padding(7.dp).size(18.dp))
        }
        Gap(6)
        Text(label,color=if(state==0) Muted else Ink,fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable private fun ApplicationFact(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(color=Color(0xFFFBF3E8),shape=CircleShape) { Icon(icon,null,tint=Brown,modifier=Modifier.padding(8.dp).size(18.dp)) }
        Column { Text(label,color=Muted,fontSize=12.sp); Text(value,color=Ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold) }
    }
}
@Composable private fun RenterApplicationDetail(m:RenterViewModel) {
    val a=m.application?:return
    val status=a.value.status
    val declined=status in listOf("REJECTED","DECLINED")
    val approved=status=="APPROVED"
    val look=appStatusLook(status)
    val homeName=a.source.lowercase().replaceFirstChar { it.uppercase() }+" home"
    val headline=when { approved->"You’re approved"; declined->"Application declined"; status=="WITHDRAWN"->"Application withdrawn"; else->"Waiting for the host" }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(look.color,look.color.copy(alpha=0.72f))))) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(color=Color.White.copy(alpha=0.22f),shape=CircleShape) { Icon(look.icon,null,tint=Color.White,modifier=Modifier.padding(14.dp).size(32.dp)) }
            Column {
                Text(headline,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.ExtraBold,lineHeight=28.sp)
                Text(homeName,color=Color.White.copy(alpha=0.88f),fontSize=14.sp)
            }
        }
    }
    Gap(16)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
        StepDot("Submitted",2,Modifier.weight(1f))
        StepDot(if(declined) "Declined" else "Host decision",if(approved) 2 else if(declined) 3 else if(status=="WITHDRAWN") 0 else 1,Modifier.weight(1f))
        StepDot("Book & pay",if(approved) 1 else 0,Modifier.weight(1f))
    }
    Gap(16)
    m.applicationCoverUrl?.let { RemoteHomePhoto(it,Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp))); Gap(12) }
    m.applicationHome?.let { h->
        Surface(color=Color(0xFFFBF3E8),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFEBD9BF)),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(h.title,color=Ink,fontSize=19.sp,fontWeight=FontWeight.ExtraBold)
                Text(kes(h.monthlyRentKes)+" / month",color=Brown,fontSize=17.sp,fontWeight=FontWeight.Bold)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.LocationOn,null,tint=Muted,modifier=Modifier.size(15.dp)); Text(h.areaLabel,color=Muted,fontSize=13.sp)
                }
            }
        }
        Gap(12)
    }
    AppAccentCard(status) {
        AppStatusBadge(status)
        ApplicationFact(Icons.Outlined.Send,"Submitted",kenyaTime(a.value.createdAt))
        ApplicationFact(Icons.Outlined.Update,"Last updated",kenyaTime(a.value.updatedAt))
        moveInLabel(a.value.moveInDate)?.let { ApplicationFact(Icons.Outlined.CalendarMonth,"Move-in date",it) }
        a.value.message?.takeIf { it.isNotBlank() }?.let { ApplicationFact(Icons.Outlined.ChatBubbleOutline,"Your message",it) }
    }
    Surface(color=look.soft,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(look.icon,null,tint=look.color)
            Text(when {
                approved -> "Approved — next step: book and pay securely with Paystack. Your payment is held until you move in."
                declined -> a.value.rejectionReason?.takeIf { it.isNotBlank() }?.let { "Reason: $it" } ?: "The host did not approve this application."
                status=="WITHDRAWN" -> "You withdrew this application."
                else -> "Waiting for the host’s decision."
            },color=Ink,fontSize=14.sp,lineHeight=20.sp)
        }
    }
    Gap(14)
    if(approved) SharedButton("Continue to booking & payment",!m.busy,m::book)
    if(declined) SharedButton("Browse similar homes",!m.busy) { m.go(RenterScreen.HOME) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick=m::viewApplicationRemark,enabled=!m.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Brown)) {
            Icon(Icons.Outlined.RateReview,null,tint=Brown,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Host remarks",color=Brown)
        }
        OutlinedButton(onClick=m::messageHost,enabled=!m.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Brown)) {
            Icon(Icons.Outlined.ChatBubbleOutline,null,tint=Brown,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Message host",color=Brown)
        }
    }
    if(m.remarkVisible) { Gap(10); SharedNote(m.applicationRemark?.takeIf { it.isNotBlank() } ?: "No remarks from the host yet.") }
    if(status in listOf("SUBMITTED","UNDER_REVIEW")) {
        var withdraw by remember { mutableStateOf(false) }
        TextButton(onClick={ withdraw=true },enabled=!m.busy,modifier=Modifier.fillMaxWidth()) { Text("Withdraw application",color=Color(0xFFB24D45),fontWeight=FontWeight.SemiBold) }
        if(withdraw) AlertDialog(onDismissRequest={withdraw=false},title={Text("Withdraw application?")},text={Text("The host will no longer see this application.")},
            confirmButton={TextButton({withdraw=false;m.withdraw()}) {Text("Confirm withdrawal")}},dismissButton={TextButton({withdraw=false}) {Text("Keep application")}})
    }
}
@Composable private fun RenterBookingDetail(m:RenterViewModel) {
    val b=m.booking?:return
    val held=b.status=="PAID_HELD"; val active=b.status=="ACTIVE"
    HeroBanner(if(active) Color(0xFF417653) else if(held) Color(0xFFA56C10) else Brown,
        if(active) Icons.Outlined.CheckCircle else if(held) Icons.Outlined.Lock else Icons.Outlined.Receipt,
        if(held) "Payment held" else if(active) "Your payment was released" else "Review your booking","Your move-in booking")
    if(held || active) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
            StepDot("Paid",2,Modifier.weight(1f))
            StepDot("Owner confirms",if(b.ownerMoveInConfirmedAt!=null) 2 else 1,Modifier.weight(1f))
            StepDot("You confirm",if(b.moveInAnsweredAt!=null) (if(b.moveInAccepted==false) 3 else 2) else if(b.ownerMoveInConfirmedAt!=null) 1 else 0,Modifier.weight(1f))
            StepDot("Released",if(active) 2 else 0,Modifier.weight(1f))
        }
        Gap(16)
    }
    HighlightCard {
        RenterStatus(if(held) "Held" else if(active) "Released" else b.status)
        AmountRow("Rent",minor(b.rentAmountMinor)); AmountRow("Deposit",minor(b.depositAmountMinor)); AmountRow("Makao Hub fee",minor(b.commissionAmountMinor))
        HorizontalDivider(color=Border)
        AmountRow("Total",minor(b.totalAmountMinor),true)
    }
    if(b.status in listOf("AWAITING_PAYMENT","PAYMENT_PENDING"))
        SharedButton("Continue to payment",!m.busy,m::bookingCheckout)
    if(b.status=="PAID_HELD" && b.ownerMoveInConfirmedAt==null) SharedText("Waiting for the owner to confirm you have moved in.",true)
    if(b.status=="PAID_HELD" && b.ownerMoveInConfirmedAt!=null && b.moveInAnsweredAt==null) {
        var ask by remember(b.id) { mutableStateOf(true) }
        HighlightCard {
            Text("Did you move in?",color=Ink,fontSize=19.sp,fontWeight=FontWeight.ExtraBold)
            SharedText("The owner says you have moved in. Answer True only if that is correct.")
            SharedButton("True, I moved in",!m.busy) { m.answerMoveIn(true) }
            SharedLink("False, I have not moved in",!m.busy) { m.answerMoveIn(false) }
        }
        if(ask) MoveInQuestionDialog("The owner says you have moved in. Is that right?",m.busy,{ ask=false;m.answerMoveIn(it) },{ ask=false })
    }
    if(b.moveInAccepted==false) SharedNote("You answered False\nNo money has been released. Your payment stays safely held while our team reviews.")
    if(b.moveInAccepted==true && b.status!="ACTIVE") SharedNote("Thank you for confirming\nYour payment is being released to the owner through Paystack.")
    b.releaseEligibleAt?.let { SharedText("Release eligible after · "+it,true) }
    b.releasedAt?.let { SharedText("Released · "+kenyaTime(it),true) }
    SharedLink("Refresh booking",!m.busy,m::refreshBooking)
}
@Composable private fun RenterCheckout(m:RenterViewModel) {
    val checkoutReturn = LocalCheckoutReturnSignal.current
    LaunchedEffect(checkoutReturn) {
        if (checkoutReturn > 0 && m.payment?.status in listOf("CREATED", "PENDING")) m.refreshPayment()
    }
    LaunchedEffect(m.payment?.id,m.payment?.status,m.screen) {
        if(m.screen==RenterScreen.PAYMENT && m.payment?.status in listOf("CREATED","PENDING")) m.watchPayment()
    }
    if(m.screen==RenterScreen.CHECKOUT) HeroBanner(Brown,Icons.Outlined.Lock,if(m.unlockType=="BOOKING") "Pay for your booking" else "Unlock access",if(m.unlockType=="BOOKING") "Rent and deposit" else paymentTitle(m.unlockType))
    else {
        val st=m.payment?.status
        HeroBanner(when(st) { "SUCCEEDED"->Color(0xFF417653);"FAILED"->Color(0xFFB24D45);else->Color(0xFFA56C10) },
            when(st) { "SUCCEEDED"->Icons.Outlined.CheckCircle;"FAILED"->Icons.Outlined.Cancel;else->Icons.Outlined.HourglassTop },
            when(st) { "SUCCEEDED"->"Payment received";"FAILED"->"Payment failed";else->"Waiting for Paystack" },
            when(st) { "SUCCEEDED"->"Thank you, your payment is complete.";"FAILED"->"No money was taken.";else->"We confirm your payment automatically, no need to tap anything." })
    }
    if(m.screen==RenterScreen.CHECKOUT) {
        val amount=if(m.unlockType=="BOOKING") m.booking?.totalAmountMinor else m.price?.amountMinor
        amount?.let { HighlightCard {
            Text(if(m.unlockType=="BOOKING") "Your booking breakdown" else paymentTitle(m.unlockType),color=Muted,fontSize=14.sp)
            Text(minor(it),color=Brown,fontSize=32.sp,fontWeight=FontWeight.ExtraBold)
            HorizontalDivider(color=Border)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.VerifiedUser,null,tint=Color(0xFF417653),modifier=Modifier.size(18.dp))
                Text("Secured by Paystack",color=Ink,fontSize=13.sp)
            }
        } }
        val enabled=!m.busy && amount!=null
        SharedButton(amount?.let { "Pay with Paystack · "+minor(it) }?:"Price unavailable",enabled,m::createPayment)
    } else m.payment?.let { p->
        HighlightCard {
            RenterStatus(paymentStatus(p))
            Text(minor(p.amountMinor),color=Brown,fontSize=32.sp,fontWeight=FontWeight.ExtraBold)
            SharedText(paymentTitle(p.paymentType),true); p.providerDisplayText?.let { SharedText(it,true) }
        }
        if(p.status=="PENDING" && !p.authorizationUrl.isNullOrBlank()) {
            val context=LocalContext.current
            SharedButton("Continue to secure payment",!m.busy) {
                runCatching {
                    require(allowedPaystackCheckoutUrl(p.authorizationUrl))
                    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(p.authorizationUrl)))
                }
                    .onFailure { m.say("Could not open the secure payment page. Refresh the payment status before retrying.") }
            }
        }
        SharedLink("Check payment status",!m.busy,m::refreshPayment)
        if(p.status=="SUCCEEDED") {
            if(p.paymentType=="BOOKING") SharedButton("View booking",!m.busy) { m.go(RenterScreen.BOOKING) }
            else SharedButton("View unlocked access",!m.busy,m::viewPaidAccess)
        }
        if(p.status=="FAILED") {
            SharedNote("The payment did not go through and no money was taken.")
            SharedButton("Retry payment",!m.busy) { m.go(if(p.paymentType=="BOOKING") RenterScreen.BOOKING else RenterScreen.DETAIL) }
        }
        if(p.status=="PENDING") SharedNote("This updates by itself after you pay. You can also tap Check payment status. Please don’t pay twice.")
    }
}
@Composable private fun RenterAccess(m:RenterViewModel) {
    HeroBanner(Color(0xFF417653),Icons.Outlined.CheckCircle,when(m.unlockType) {"PHONE_UNLOCK"->"Your unlocked contact";"VIDEO_UNLOCK"->"Your video tour";else->"Your unlocked location"},"Unlocked for this home")
    m.location?.let { location->
        val context=LocalContext.current
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) { RenterAreaMap(LatLng(location.latitude.toDouble(),location.longitude.toDouble()),exact=true) }
        Gap(12)
        HighlightCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(color=Color(0xFFF5C26B),shape=CircleShape) { Icon(Icons.Outlined.LocationOn,null,tint=Brown,modifier=Modifier.padding(10.dp).size(22.dp)) }
                Column {
                    Text(location.areaLabel,color=Ink,fontSize=19.sp,fontWeight=FontWeight.ExtraBold)
                    Text("Exact pin · ${location.latitude}, ${location.longitude}",color=Muted,fontSize=13.sp)
                }
            }
            SharedText("Viewing\nArrange a time using protected chat.")
        }
        SharedButton("Open directions",true) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}"))) }
                .onFailure { m.say("No maps app is available on this device.") }
        }
    }
    m.phone?.let { HighlightCard {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(color=Color(0xFFF5C26B),shape=CircleShape) { Icon(Icons.Outlined.Call,null,tint=Brown,modifier=Modifier.padding(12.dp).size(26.dp)) }
            Column {
                Text(it.ownerName,color=Ink,fontSize=20.sp,fontWeight=FontWeight.ExtraBold)
                Text(it.phoneNumber,color=Brown,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    } }
    m.videos.forEach { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) { ProtectedVideo(it.signedReadUrl) }; Gap(12) }
    if(m.unlockType=="VIDEO_UNLOCK" && m.videos.isEmpty()) SharedText("No video is available.",true)
    SharedLink("Back to listing",true) { m.go(RenterScreen.DETAIL) }
}
@Composable private fun RenterVerification(m:RenterViewModel) {
    var camera by remember { mutableStateOf<RenterEvidence?>(null) }
    var choosing by remember { mutableStateOf<RenterEvidence?>(null) }
    var correcting by remember { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    var sourceFor by remember { mutableStateOf<RenterEvidence?>(null) }
    val resolver=LocalContext.current.contentResolver
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->val type=choosing;choosing=null;if(uri!=null && type!=null) m.upload(type,resolver,uri) }
    val status=m.verification?.status
    val terminal=status in listOf("PENDING","VERIFIED") || status=="REJECTED" && !correcting
    val editable=status in listOf("NOT_STARTED","REJECTED")
    fun capture(type:RenterEvidence) {
        val stored=m.documents.firstOrNull { it.documentType==type.name }
        when {
            m.pendingEvidenceType==type.name -> m.retryEvidence()
            stored?.status=="UPLOADING" -> m.confirmStored(stored.documentId)
            type==RenterEvidence.SELFIE -> camera=type
            else -> sourceFor=type
        }
    }
    val back={ if(!m.busy) {
        if(camera!=null) camera=null else if(started && !terminal) started=false else m.go(RenterScreen.HOME)
    } }
    BackHandler(onBack=back)
    sourceFor?.let { type->IdSourceDialog(type.title,{sourceFor=null},
        {sourceFor=null;camera=type},{sourceFor=null;choosing=type;picker.launch(arrayOf("image/jpeg","image/png"))}) }
    VerificationPageBody(back, { m.go(RenterScreen.HOME) }, !m.busy, if (!started && !terminal) "Verification" else "Capture documents") {
    m.notice?.let { SharedNote(it); Gap(8) }
    when {
        terminal -> VerificationOutcomeScreen(
            role="Renter", status=status!!, rejectionReason=m.verification?.rejectionReason,
            busy=m.busy, approvedAction="Explore homes",
            onApproved={m.go(RenterScreen.HOME)}, onCorrect={correcting=true},
            onClose={m.go(RenterScreen.HOME)}, onRefresh=m::refreshVerification)
        !started && editable -> VerificationIntro("Renter", "Confirms your identity for your renter account.", null) { started = true }
        camera!=null -> {
            val type=camera!!
            Heading(if(type==RenterEvidence.SELFIE) "Capture selfie" else type.title);Gap(20)
            key(type) { EvidenceCamera(type==RenterEvidence.SELFIE,editable&&!m.busy,{bitmap->camera=null;m.capture(type,bitmap)},m::say) }
            SharedLink("Cancel camera",!m.busy) { camera=null }
        }
        else -> {
            val types=listOf(RenterEvidence.ID_FRONT,RenterEvidence.ID_BACK,RenterEvidence.SELFIE)
            IdentityCaptureChecklist(
                role="Renter",busy=m.busy,editable=editable,stage=m.evidenceStage,progress=m.evidenceProgress,
                items=types.map { type->IdentityCaptureItem(
                    type.name,
                    when(type) { RenterEvidence.ID_FRONT->"National ID (front)";RenterEvidence.ID_BACK->"National ID (back)";else->"Selfie" },
                    when(type) { RenterEvidence.ID_FRONT->"Photo side, fit the card inside the frame";RenterEvidence.ID_BACK->"Turn the card over, all four corners visible";else->"Remove spectacles, face the camera" },
                    m.pendingEvidenceType!=type.name && renterEvidenceReady(type,m.documents),
                    m.busy && m.pendingEvidenceType==type.name
                ) },
                onCapture={key->capture(RenterEvidence.valueOf(key))},onContinue=m::submitEvidence)
        }
    }
    }
}
@Composable private fun RenterHeading(title:String,subtitle:String) { Text(title,color=Brown,fontSize=25.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp);Gap(10);SharedText(subtitle,true);Gap(20) }
@Composable private fun RenterInput(label:String,value:String,enabled:Boolean,lines:Int=1,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,enabled=enabled,label={Text(label)},singleLine=lines==1,minLines=lines,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().padding(bottom=12.dp))
}
@Composable private fun RenterStatus(value:String) {
    val normalized=value.uppercase();val color=when {
        normalized.contains("FAILED") || normalized.contains("REJECTED") -> androidx.compose.ui.graphics.Color(0xFFB24D45)
        normalized.contains("PENDING") || normalized.contains("REVIEW") || normalized.contains("HELD") || normalized.contains("DISPUTED") -> androidx.compose.ui.graphics.Color(0xFFA56C10)
        normalized.contains("VERIFIED") || normalized.contains("PAID") || normalized.contains("APPROVED") || normalized.contains("RELEASED") || normalized.contains("SUCCEEDED") -> androidx.compose.ui.graphics.Color(0xFF417653)
        else -> Brown
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color,CircleShape));Text(value.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() },color=color,fontSize=12.sp)
    }
}
private fun paymentTitle(value:String)=when(value) { "EXACT_LOCATION_UNLOCK"->"Location access";"VIDEO_UNLOCK"->"Video access";"PHONE_UNLOCK"->"Phone access";"BOOKING"->"Booking";else->value.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() } }
private fun paymentStatus(value:OwnerPayment)=if(value.status=="SUCCEEDED") "Paid" else value.status
private fun kes(value:Long)="KSh "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.UK).format(value)
private fun minor(value:Long)="KSh "+BigDecimal.valueOf(value,2).toPlainString()
private val photoClient=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(20,TimeUnit.SECONDS).build()
internal fun allowedHomeMedia(url:String):Boolean=runCatching { val u=url.toHttpUrl();u.isHttps && u.host.endsWith(".supabase.co") && u.username.isEmpty() && u.password.isEmpty() && u.encodedPath.startsWith("/storage/v1/object/sign/") }.getOrDefault(false)
internal fun allowedPaystackCheckoutUrl(value:String):Boolean=runCatching {
    val u=java.net.URI(value)
    u.scheme.equals("https",ignoreCase=true) && u.host.equals("checkout.paystack.com",ignoreCase=true) && u.userInfo==null && u.port in listOf(-1,443) &&
        u.rawPath?.matches(Regex("/[A-Za-z0-9-]+"))==true && u.rawFragment==null
}.getOrDefault(false)
@Composable internal fun RemoteHomePhoto(url:String, modifier:Modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp))) {
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
    bitmap?.let { Image(it.asImageBitmap(),contentDescription="Listing photo",contentScale=ContentScale.Crop,modifier=modifier) }
    if(failed) SharedText("Photo unavailable",true)
}
@Composable private fun ProtectedVideo(url:String) {
    if(!allowedHomeMedia(url)) { SharedText("Video unavailable",true);return }
    val context=LocalContext.current
    val player=remember(url) { VideoView(context).apply { setVideoURI(Uri.parse(url));setMediaController(MediaController(context).also { it.setAnchorView(this) }) } }
    DisposableEffect(player) { onDispose { player.stopPlayback() } }
    AndroidView(factory={player},modifier=Modifier.fillMaxWidth().height(230.dp))
}
