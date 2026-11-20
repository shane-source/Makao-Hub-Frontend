package com.makaohub.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerBooking

private fun kes(minor: Long) = "KSh " + "%,d".format(minor / 100)

private fun payoutStatusLabel(code: String): String = when (code) {
    "SUCCEEDED" -> "paid to your M-Pesa"
    "QUEUED" -> "queued"
    "PENDING" -> "in progress with Paystack"
    "OTP" -> "waiting for Paystack confirmation"
    "FAILED" -> "failed"
    "REVERSED" -> "reversed"
    "REVIEW", "REVIEW_REQUIRED" -> "under review by our team"
    "DESTINATION_REQUIRED" -> "add your M-Pesa number to receive it"
    "OWNER_NOT_VERIFIED" -> "waiting for owner verification"
    else -> code.lowercase().replace('_', ' ')
}

/**
 * Landlord/sublessor side of move-in. Shows the booking tied to this application and the next step:
 * confirm the booking terms, then "Proceed to confirm move-in", then wait for the renter's True/False.
 */
@Composable
internal fun OwnerMoveInCard(
    booking: OwnerBooking?,
    busy: Boolean,
    payoutReady: Boolean?,
    onSetupPayout: () -> Unit,
    onConfirmMoveIn: () -> Unit,
    onRefresh: () -> Unit
) {
    if (booking == null || booking.status !in listOf("PAID_HELD", "ACTIVE")) return
    var confirming by remember(booking.id) { mutableStateOf(false) }
    val renter = booking.renterName ?: "the renter"

    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Move-in & payment", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            when {
                booking.status == "ACTIVE" || booking.releasedAt != null || booking.testPayoutStatus == "SUCCEEDED" -> {
                    Text("Payment released", fontWeight = FontWeight.SemiBold)
                    Text("${kes(booking.totalAmountMinor)} was released to you through Paystack. Welcome your new tenant!")
                }
                booking.moveInAccepted == false -> {
                    Text("$renter answered False", fontWeight = FontWeight.SemiBold)
                    Text("No money has been released. ${kes(booking.totalAmountMinor)} stays safely held while our team reviews and contacts you both.")
                }
                booking.moveInAccepted == true -> {
                    Text("$renter confirmed move-in", fontWeight = FontWeight.SemiBold)
                    Text("Your payment of ${kes(booking.totalAmountMinor)} is on its way through Paystack."
                            + (booking.testPayoutStatus?.takeIf { it != "NOT_QUEUED" }?.let { "\nStatus: " + payoutStatusLabel(it) } ?: ""))
                }
                booking.ownerMoveInConfirmedAt != null -> {
                    Text("Waiting for $renter", fontWeight = FontWeight.SemiBold)
                    Text("We asked $renter to answer True or False. Your payment is held until they reply.")
                }
                else -> {
                    Text("${kes(booking.totalAmountMinor)} is held safely. Once $renter has moved in, confirm it here.")
                    if (payoutReady == false) {
                        Text("Add your M-Pesa number first so Paystack knows where to send your payment.", fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onSetupPayout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Set up payout") }
                    }
                    Button({ confirming = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Proceed to confirm move-in") }
                }
            }
            TextButton(onRefresh, enabled = !busy) { Text("Refresh status") }
        }
    }

    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Has $renter moved in?") },
        text = {
            Text("Only confirm if $renter has the keys and has moved in. We'll ask them to answer True or False. " +
                    "If they confirm, your payment is released through Paystack. If they answer False, nothing is released and our team steps in.")
        },
        confirmButton = { TextButton({ confirming = false; onConfirmMoveIn() }) { Text("Yes, they moved in") } },
        dismissButton = { TextButton({ confirming = false }) { Text("Not yet") } }
    )
}

/** Renter pop-up: "Did you move in?" with True / False. */
@Composable
internal fun MoveInQuestionDialog(
    body: String,
    busy: Boolean,
    onAnswer: (Boolean) -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Did you move in?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                Text("True releases your payment to the owner. False keeps your money safely held and our team will review.",
                    fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ onAnswer(true) }, enabled = !busy) { Text("True, I moved in") } },
        dismissButton = {
            Row {
                TextButton({ onAnswer(false) }, enabled = !busy) { Text("False") }
                TextButton(onLater, enabled = !busy) { Text("Later") }
            }
        }
    )
}

/** Plain heads-up pop-up, used for the owner when a renter answers. */
@Composable
internal fun MoveInResultDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onDismiss) { Text("OK") } }
    )
}
