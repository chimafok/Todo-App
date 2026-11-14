package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.animation.core.tween

@Composable
internal fun NoteImageViewer(ids: List<String>, initialId: String, onViewingChanged: (String) -> Unit, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = ids.indexOf(initialId).coerceAtLeast(0), pageCount = { ids.size })
    var zoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    var requestedPage by remember { mutableIntStateOf(pager.currentPage) }
    val navigationPage = if (navigationJob?.isActive == true) requestedPage else pager.currentPage
    fun moveBy(delta: Int) {
        val target = ((if (navigationJob?.isActive == true) requestedPage else pager.currentPage) + delta).coerceIn(0, ids.lastIndex)
        requestedPage = target
        navigationJob?.cancel()
        navigationJob = scope.launch { pager.animateScrollToPage(target, animationSpec = tween(120)) }
    }
    val changed by rememberUpdatedState(onViewingChanged)
    LaunchedEffect(pager, ids) {
        snapshotFlow { pager.settledPage }.collect { page -> ids.getOrNull(page)?.let(changed) }
    }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.text_image_s_s_cae5c2, pager.currentPage + 1, ids.size),
                        Modifier.weight(1f).padding(16.dp).testTag("note_image_counter"))
                    ActionIconButton(R.drawable.ic_close, tr(R.string.text_close_c7fddd),
                        Modifier.testTag("note_image_close"), onClick = onDismiss)
                }
                HorizontalPager(state = pager, userScrollEnabled = !zoomed,
                    key = { ids[it] }, modifier = Modifier.weight(1f).fillMaxWidth().testTag("note_image_pager")) { page ->
                    NoteImage(ids[page], false, tr(R.string.text_image_s_911ad3, page + 1), Modifier.fillMaxSize(),
                        onBackgroundTap = onDismiss, active = pager.currentPage == page,
                        onZoomChanged = { if (pager.currentPage == page) zoomed = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionIconButton(R.drawable.ic_chevron_left, tr(R.string.text_previous_image_a3b6d8),
                        Modifier.testTag("note_image_previous"), enabled = navigationPage > 0,
                        onClick = { moveBy(-1) })
                    ActionIconButton(R.drawable.ic_chevron_right, tr(R.string.text_next_image_c3b16d),
                        Modifier.testTag("note_image_next"), enabled = navigationPage < ids.lastIndex,
                        onClick = { moveBy(1) })
                }
            }
        }
    }
}
