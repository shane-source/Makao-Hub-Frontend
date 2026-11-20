package com.makaohub.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.makaohub.android.data.LandlordOffers
import com.makaohub.android.data.OwnerPayment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun ListingBoost(
    listingId: String,
    published: Boolean,
    boosted: Boolean,
    busy: Boolean,
    offers: suspend () -> LandlordOffers,
    checkout: suspend (String, String, String) -> OwnerPayment,
    reconcile: suspend (String) -> OwnerPayment,
    refresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var open by remember(listingId) { mutableStateOf(false) }
    var loading by remember(listingId) { mutableStateOf(false) }
    var options by remember(listingId) { mutableStateOf<LandlordOffers?>(null) }
    var payment by remember(listingId) { mutableStateOf<OwnerPayment?>(null) }
    var attempt by remember(listingId) { mutableStateOf(UUID.randomUUID().toString()) }
    var selectedCode by remember(listingId) { mutableStateOf<String?>(null) }
    var error by remember(listingId) { mutableStateOf<String?>(null) }
    Button(onClick = {
        open = true
        if (published && options == null && payment == null) scope.launch {
            loading = true; error = null
            try { options = offers() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Boost offers are unavailable. Please try again later." }
            finally { loading = false }
        }
    }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDEA5), contentColor = Brown),
        modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.RocketLaunch, null); Spacer(Modifier.width(8.dp)); Text(if (boosted) "Boosted" else "Boost listing")
    }
    if (open) AlertDialog(onDismissRequest = { if (!loading) open = false },
        title = { Text("Boost listing") },
        text = {
            Column {
                if (!published) Text("Publish this listing before boosting it.")
                else if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                else if (payment != null) {
                    val current = payment!!
                    Text("Payment status: ${current.status.lowercase()}")
                    if (current.status == "PENDING" && !current.authorizationUrl.isNullOrBlank()) {
                        TextButton(onClick = {
                            try {
                                require(allowedPaystackCheckoutUrl(current.authorizationUrl))
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.authorizationUrl)))
                            } catch (_: Exception) { error = "Could not open the payment page." }
                        }) { Text("Continue to secure payment") }
                    }
                    if (current.status == "PENDING" && !current.providerReference.isNullOrBlank()) {
                        TextButton(onClick = { scope.launch {
                            loading = true; error = null
                            try { payment = reconcile(current.providerReference); if (payment?.status == "SUCCEEDED") refresh() }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Could not check payment. Please retry with the same payment." }
                            finally { loading = false }
                        } }) { Text("Check payment status") }
                    }
                } else {
                    Text("Choose how long to boost this listing.", style = MaterialTheme.typography.bodyMedium)
                    options?.offers?.filter { it.code.contains("BOOST_") }?.sortedBy { it.days }?.forEach { offer ->
                        val shillings = offer.amountMinor / 100
                        val cents = offer.amountMinor % 100
                        val price = if (cents == 0L) "KSh $shillings" else "KSh $shillings.${cents.toString().padStart(2, '0')}"
                        val length = if (offer.days == 1L) "1 day" else "${offer.days} days"
                        OutlinedButton(onClick = { scope.launch {
                            if (selectedCode != null && selectedCode != offer.code) {
                                error = "Retry the same offer until the previous request is resolved."; return@launch
                            }
                            selectedCode = offer.code; loading = true; error = null
                            try { payment = checkout(offer.code, listingId, attempt) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Could not start checkout. Retry the same offer." }
                            finally { loading = false }
                        } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("$length boost · $price")
                                if (offer.estimatedReach > 0) Text(
                                    "Reaches up to ~${"%,d".format(offer.estimatedReach)} renters (estimate)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }, enabled = !loading) { Text("Close") } })
}
