package com.makaohub.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** Same bounded listing counts used by the server-side request validation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListingCountDropdown(label: String, value: String, numbers: IntRange, enabled: Boolean, allowAny: Boolean = false, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = if (allowAny && value.isBlank()) "Any" else value, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true)
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowAny) DropdownMenuItem(text = { Text("Any") }, onClick = { choose(""); expanded = false })
            numbers.forEach { number -> DropdownMenuItem(text = { Text(number.toString()) }, onClick = {
                choose(number.toString()); expanded = false
            }) }
        }
    }
    Spacer(Modifier.height(14.dp))
}
