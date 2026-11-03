package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.*
import kotlinx.coroutines.launch

@Composable
fun TrashSelectionDialog(preview: TrashSelectionPreview, restore: Boolean, viewModel: TodoViewModel,
    onDismiss: ()->Unit, onSaved: ()->Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},modifier=Modifier.testTag("trash_selection_dialog"),
        title={Text(if(restore) tr(R.string.text_restore_selected_items_345a83) else tr(R.string.text_permanently_delete_selected_items_bab6b0))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr(R.string.text_s_series_s_tasks_defc68, preview.series.size, if(restore)preview.restoreTasks.size else preview.purge.tasks.size))
            if(restore) Text(tr(R.string.text_restore_selected_tasks_selecting_a_series_d43a52))
            else Text(tr(R.string.text_permanently_delete_selected_tasks_and_all_a7a02f))
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("selection_error"))}
        }},
        confirmButton={TextButton(enabled=!busy,modifier=Modifier.testTag("confirm_trash_selection"),onClick={
            busy=true;error=null
            scope.launch {try {viewModel.applyTrashSelection(preview,restore);onSaved()}
                catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: tr(R.string.text_operation_failed_please_select_again_3b3f4e)}
                finally {busy=false}}
        }) {Text(if(busy)tr(R.string.text_processing_d166e7) else if(restore)tr(R.string.text_restore_selected_items_239683) else tr(R.string.text_delete_permanently_667198))}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.testTag("cancel_trash_selection")){Text(tr(R.string.text_cancel_2cd0f3))}})
}

@Composable
fun SeriesSelectionDialog(preview: SeriesSelectionPreview, stopOnly: Boolean, viewModel: TodoViewModel,
    onDismiss: ()->Unit,onSaved: ()->Unit) {
    var includeHistory by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},modifier=Modifier.testTag("series_selection_dialog"),
        title={Text(if(stopOnly)tr(R.string.text_stop_selected_recurring_series_af822e) else tr(R.string.text_delete_selected_series_139a93))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.text_s_series_selected_70f058, preview.items.size))
            if(stopOnly)Text(tr(R.string.text_stop_generating_future_tasks_keep_generated_b237f5))
            else {
                Text(tr(R.string.text_by_default_stop_recurrence_and_move_33fdf1, preview.items.values.sumOf {it.futureCount}))
                Row {Checkbox(includeHistory,onCheckedChange={includeHistory=it},enabled=!busy,modifier=Modifier.testTag("include_selected_history"))
                    Text(tr(R.string.text_include_all_history_65529c),Modifier.padding(top=12.dp))}
                if(includeHistory)Text(tr(R.string.text_move_the_selected_series_and_s_8db86c, preview.items.values.sumOf {it.activeCount}))
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("selection_error"))}
        }},
        confirmButton={TextButton(enabled=!busy,modifier=Modifier.testTag("confirm_series_selection"),onClick={
            busy=true;error=null
            scope.launch {try {viewModel.applySeriesSelection(preview,stopOnly,includeHistory);onSaved()}
                catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: tr(R.string.text_operation_failed_please_select_again_3b3f4e)}
                finally {busy=false}}
        }) {Text(if(busy)tr(R.string.text_processing_d166e7) else tr(R.string.text_confirm_dce52f))}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.testTag("cancel_series_selection")){Text(tr(R.string.text_cancel_2cd0f3))}})
}

@Composable
fun LostSelectionDialog(onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr(R.string.text_please_select_again_d4f3af))},text={Text(tr(R.string.text_the_app_restarted_and_the_previous_98814b))},
        confirmButton={TextButton(onClick=onDismiss){Text(tr(R.string.text_close_c7fddd))}})
}
