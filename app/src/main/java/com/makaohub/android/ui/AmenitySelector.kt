package com.makaohub.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makaohub.android.data.AmenityOption

@Composable
internal fun AmenitySelector(options: List<AmenityOption>, selected: Set<String>, enabled: Boolean, onChange: (String, Boolean) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected.isEmpty()) "Select amenities" else "Select amenities · ${selected.size} selected")
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("No amenities available") }, onClick = {}, enabled = false)
            }
            options.forEach { amenity ->
                val checked = amenity.id in selected
                DropdownMenuItem(
                    text = { Text(amenity.name) },
                    enabled = checked || selected.size < 50,
                    leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                    onClick = { onChange(amenity.id, !checked) }
                )
            }
        }
    }
}
