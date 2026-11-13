package com.chimafok.todo.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

/** Shared accessible action: the same localized label is used for speech and long-press help. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionIconButton(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean? = null, badgeCount: Int = 0,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    containerColor: Color = Color.Transparent, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, enabled = enabled,
            modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                selected?.let { this.selected = it }
            }, colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected == true) MaterialTheme.colorScheme.primaryContainer else containerColor,
                contentColor = if (selected == true) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
                disabledContainerColor = if (selected == true) MaterialTheme.colorScheme.primaryContainer else containerColor,
                disabledContentColor = if (disabledContentColor == contentColor && selected == true) MaterialTheme.colorScheme.onPrimaryContainer else disabledContentColor)) {
            if (badgeCount > 0) BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
                Icon(painterResource(icon), label, Modifier.size(24.dp))
            } else Icon(painterResource(icon), label, Modifier.size(24.dp))
        }
    }
}
