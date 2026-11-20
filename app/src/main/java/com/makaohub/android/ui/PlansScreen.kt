package com.makaohub.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.LandlordOfferGrant
import com.makaohub.android.data.LandlordOfferQuote
import com.makaohub.android.data.LandlordOffers
import com.makaohub.android.data.OwnerPayment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Plans screen shared by landlords and sublessors: Free, Pro and Pro Max, with expiry and the included boost. */
@Composable
internal fun PlansPanel(
    offers: suspend () -> LandlordOffers,
    buyPlan: suspend (String, String) -> OwnerPayment,
    reconcile: suspend (String) -> OwnerPayment,
    publishedListings: List<Pair<String, String>>,
    redeemBoost: suspend (String) -> Unit,
    refresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var info by remember { mutableStateOf<LandlordOffers?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var payment by remember { mutableStateOf<OwnerPayment?>(null) }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var pickingBoost by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try { info = offers(); error = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Plans are unavailable right now. Please try again later." }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }

    OwnerHeading("Choose a plan that fits", "Plans last 30 days")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
    message?.let { Text(it, color = Brown, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)) }
    if (loading && info == null) { CircularProgressIndicator(Modifier.size(24.dp)); return }
    val current = info ?: return

    val activePlan = activePlanGrant(current.activeGrants)
    val activeName = activePlan?.let { planName(it.code) } ?: "Free"
    OwnerCard("Your plan") {
        Text(activeName, color = Brown, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(photoText(current.activePhotoLimit), color = Muted, fontSize = 14.sp)
        if (activePlan != null) {
            val (date, days) = expiry(activePlan.expiresAt)
            val soon = days in 0..3
            Text(if (days < 0) "Expires on $date" else "Expires on $date · $days ${if (days == 1L) "day" else "days"} left",
                color = if (soon) MaterialTheme.colorScheme.error else Muted, fontSize = 14.sp,
                fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal)
            if (soon) Text("Renew soon to keep your plan. Photos you already added are kept either way.", color = Muted, fontSize = 13.sp)
        } else {
            Text("Upgrade any time to add more photos.", color = Muted, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(12.dp))

    PlanOption("Free", "KSh 0", listOf("${current.freePhotoLimit} photos per listing", "${current.starterLimit} active listing"),
        active = activePlan == null, actionLabel = null, enabled = false) {}
    val pro = current.offers.firstOrNull { it.code.removePrefix("SUB_") == "PRO_30D" }
    val proMax = current.offers.firstOrNull { it.code.removePrefix("SUB_") == "PRO_MAX_30D" }
    listOfNotNull(pro, proMax).forEach { quote ->
        val max = quote.code.removePrefix("SUB_") == "PRO_MAX_30D"
        val points = buildList {
            add(photoText(quote.photoLimit))
            add("${quote.listingLimit} active listings")
            add("Listing insights")
            if (quote.includedBoosts > 0) add("${quote.includedBoosts} boost included (7 days)")
        }
        val isCurrent = activePlan != null && planName(activePlan.code) == (if (max) "Pro Max" else "Pro")
        PlanOption(if (max) "Pro Max" else "Pro", kes(quote.amountMinor) + " / 30 days", points, active = isCurrent,
            actionLabel = if (isCurrent) "Renew ${if (max) "Pro Max" else "Pro"}" else "Choose ${if (max) "Pro Max" else "Pro"}",
            enabled = !loading) {
            scope.launch {
                if (selectedCode != null && selectedCode != quote.code) {
                    error = "Finish or retry the previous payment before choosing another plan."; return@launch
                }
                selectedCode = quote.code; loading = true; error = null
                try { payment = buyPlan(quote.code, attempt) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Could not start checkout. Please try again." }
                finally { loading = false }
            }
        }
    }

    if (current.boostCredits > 0) {
        Spacer(Modifier.height(4.dp))
        OwnerCard("Your included boost") {
            Text("Your Pro Max plan includes a 7-day boost. Use it on any published listing.", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { pickingBoost = true }, enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDEA5), contentColor = Brown),
                modifier = Modifier.fillMaxWidth()) { Text("Use my included boost") }
        }
    }

    if (pickingBoost) AlertDialog(onDismissRequest = { pickingBoost = false },
        title = { Text("Boost which listing?") },
        text = {
            Column {
                if (publishedListings.isEmpty()) Text("Publish a listing first, then you can boost it.")
                publishedListings.forEach { (id, title) ->
                    TextButton(onClick = {
                        pickingBoost = false
                        scope.launch {
                            loading = true; error = null
                            try {
                                redeemBoost(id); message = "Boost started for 7 days."
                                load(); refresh()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Could not use the boost. It may already be boosted." }
                            finally { loading = false }
                        }
                    }) { Text(title, maxLines = 1) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickingBoost = false }) { Text("Cancel") } })

    payment?.let { paid ->
        AlertDialog(onDismissRequest = { if (!loading) { payment = null } },
            title = { Text("Plan payment") },
            text = {
                Column {
                    Text("Payment status: ${paid.status.lowercase()}")
                    val url = paid.authorizationUrl
                    if (paid.status == "PENDING" && !url.isNullOrBlank()) {
                        TextButton(onClick = {
                            try {
                                require(allowedPaystackCheckoutUrl(url))
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) { error = "Could not open the payment page." }
                        }) { Text("Continue to secure payment") }
                    }
                    val reference = paid.providerReference
                    if (paid.status == "PENDING" && !reference.isNullOrBlank()) {
                        TextButton(onClick = { scope.launch {
                            loading = true; error = null
                            try {
                                val result = reconcile(reference); payment = result
                                if (result.status == "SUCCEEDED") {
                                    message = "Plan activated."; selectedCode = null; attempt = UUID.randomUUID().toString()
                                    load(); refresh()
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Could not check the payment. Please try again." }
                            finally { loading = false }
                        } }) { Text("Check payment status") }
                    }
                    if (paid.status == "FAILED") Text("The payment did not go through. Close this and try again.")
                }
            },
            confirmButton = { TextButton(onClick = {
                if (paid.status == "SUCCEEDED" || paid.status == "FAILED") { selectedCode = null; attempt = UUID.randomUUID().toString() }
                payment = null
            }, enabled = !loading) { Text("Close") } })
    }
}

@Composable
private fun PlanOption(name: String, price: String, points: List<String>, active: Boolean, actionLabel: String?,
    enabled: Boolean, onAction: () -> Unit) {
    OwnerCard(name) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(price, color = Brown, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (active) Text("Current plan", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        points.forEach { Text("• $it", fontSize = 14.sp) }
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Brown, contentColor = Color.White)) { Text(actionLabel) }
        }
    }
    Spacer(Modifier.height(10.dp))
}

private fun photoText(limit: Int) = if (limit < 0) "Unlimited photos per listing" else "$limit photos per listing"
private fun kes(minor: Long) = "KSh " + "%,d".format(minor / 100)
private fun planName(code: String) = if (code.contains("PRO_MAX")) "Pro Max" else "Pro"

private fun activePlanGrant(grants: List<LandlordOfferGrant>): LandlordOfferGrant? {
    val plans = grants.filter { it.listingId == null }
    return plans.firstOrNull { it.code.contains("PRO_MAX") } ?: plans.firstOrNull()
}

/** Returns a Kenya-time date and whole days remaining (negative if the date can't be read). */
private fun expiry(iso: String): Pair<String, Long> = try {
    val instant = Instant.parse(iso)
    val date = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Africa/Nairobi")).format(instant)
    date to Duration.between(Instant.now(), instant).toDays()
} catch (_: Exception) { "soon" to -1L }
