package com.makaohub.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerTestDestinationStatus

/** Where the owner's released payment is sent: their own M-Pesa number. It can be set once. */
@Composable
internal fun OwnerTestPayoutSetup(status: OwnerTestDestinationStatus?, busy: Boolean, onSave: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var mine by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Payout number", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            when {
                status == null -> Text("Checking your payout details…")
                status.configured -> Text("Your M-Pesa payout number is saved. Released payments are sent there.")
                else -> {
                    Text("Add the M-Pesa number that should receive your payments.")
                    OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("M-Pesa number (07XXXXXXXX)") },
                        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(mine, { mine = it }, enabled = !busy)
                        Text("This is my own number. It can’t be changed later.", fontSize = 13.sp)
                    }
                    Button({ onSave(phone) }, enabled = !busy && mine && phone.length == 10, modifier = Modifier.fillMaxWidth()) {
                        Text("Save payout number")
                    }
                }
            }
        }
    }
}
