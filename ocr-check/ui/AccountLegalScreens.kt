package com.makaohub.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class LegalDraft { PRIVACY, TERMS }

@Composable
fun AccountLegalDialog(draft: LegalDraft, close: () -> Unit) {
    val title = if (draft == LegalDraft.PRIVACY) "Privacy notice" else "Terms of Service"
    val body = if (draft == LegalDraft.PRIVACY)
        "Makao Hub uses your account details to provide authentication, verification, listings, messaging and payments. Identity documents, exact property locations and protected contact details are not public. You may request deactivation or deletion from your profile. Some transaction and safety records may be retained where required for fraud prevention, disputes and legal obligations."
    else
        "Use Makao Hub lawfully and provide accurate account, listing and verification information. Applications do not reserve a home, and access purchases do not create a tenancy. Payment and booking states shown by the server are authoritative. Do not misuse another person’s documents, contact details or account. Active bookings, held funds, payouts or disputes must be resolved before an account can be closed."
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { Text(body) }
    }, confirmButton = { TextButton(close) { Text("Close") } })
}
