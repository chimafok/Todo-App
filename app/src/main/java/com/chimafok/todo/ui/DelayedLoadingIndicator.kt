package com.chimafok.todo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Presentation delay only; queries and stale-action guards still run immediately. */
@Composable
internal fun DelayedLoadingIndicator(loading: Boolean, modifier: Modifier = Modifier, requestKey: Any? = Unit) {
    var visible by remember(requestKey) { mutableStateOf(false) }
    LaunchedEffect(loading, requestKey) {
        visible = false
        if (loading) { delay(2_000); visible = true }
    }
    // Keep a stable slot so the list does not jump when a slow query shows progress.
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (loading && visible) LinearProgressIndicator(modifier.fillMaxWidth())
    }
}
