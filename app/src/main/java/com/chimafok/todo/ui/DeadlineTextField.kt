package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Static label avoids the dark floating-label cutout over a filled outlined field. */
@Composable
internal fun DeadlineTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
    colors: TextFieldColors, modifier: Modifier = Modifier, enabled: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value,onValueChange,modifier.semantics { contentDescription = label },
            enabled=enabled,singleLine=true,placeholder={Text(placeholder)},supportingText=supportingText,
            trailingIcon=trailingIcon,colors=colors,shape=MaterialTheme.shapes.medium)
    }
}
