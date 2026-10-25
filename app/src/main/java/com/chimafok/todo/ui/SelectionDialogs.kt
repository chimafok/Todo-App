package com.chimafok.todo.ui

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
        title={Text(if(restore) "復原所選項目？" else "永久刪除所選項目？")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${preview.series.size} 個系列、${if(restore)preview.restoreTasks.size else preview.purge.tasks.size} 項任務")
            if(restore) Text("復原選取任務；選取系列會一併復原同次刪除的剩餘紀錄。先前單獨刪除的紀錄不會跟著復原，除非也明確選取；已永久刪除者無法復原。原本重複中的系列會恢復生成。")
            else Text("永久刪除選取任務，以及選取系列的全部剩餘紀錄；刪除後無法復原。未選取的其他系列及任務保留。")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("selection_error"))}
        }},
        confirmButton={TextButton(enabled=!busy,modifier=Modifier.testTag("confirm_trash_selection"),onClick={
            busy=true;error=null
            scope.launch {try {viewModel.applyTrashSelection(preview,restore);onSaved()}
                catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: "操作失敗，請重新選取。"}
                finally {busy=false}}
        }) {Text(if(busy)"處理中…" else if(restore)"復原所選項目" else "永久刪除")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.testTag("cancel_trash_selection")){Text("取消")}})
}

@Composable
fun SeriesSelectionDialog(preview: SeriesSelectionPreview, stopOnly: Boolean, viewModel: TodoViewModel,
    onDismiss: ()->Unit,onSaved: ()->Unit) {
    var includeHistory by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},modifier=Modifier.testTag("series_selection_dialog"),
        title={Text(if(stopOnly)"停止所選重複系列？" else "刪除所選系列？")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("已選 ${preview.items.size} 個系列")
            if(stopOnly)Text("停止之後生成，已產生的任務、提醒與全部歷史保留。")
            else {
                Text("預設停止重複，將 ${preview.items.values.sumOf {it.futureCount}} 項未來未完成任務移至垃圾桶；依各系列時區保留今天、過去及全部已完成紀錄。")
                Row {Checkbox(includeHistory,onCheckedChange={includeHistory=it},enabled=!busy,modifier=Modifier.testTag("include_selected_history"))
                    Text("包含全部歷史",Modifier.padding(top=12.dp))}
                if(includeHistory)Text("所選系列與 ${preview.items.values.sumOf {it.activeCount}} 項未刪紀錄一併移至垃圾桶，30天內可復原。")
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("selection_error"))}
        }},
        confirmButton={TextButton(enabled=!busy,modifier=Modifier.testTag("confirm_series_selection"),onClick={
            busy=true;error=null
            scope.launch {try {viewModel.applySeriesSelection(preview,stopOnly,includeHistory);onSaved()}
                catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: "操作失敗，請重新選取。"}
                finally {busy=false}}
        }) {Text(if(busy)"處理中…" else "確認")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.testTag("cancel_series_selection")){Text("取消")}})
}

@Composable
fun LostSelectionDialog(onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("請重新選取")},text={Text("程式已重新啟動，舊操作範圍不再有效。請關閉後重新選取並預覽。")},
        confirmButton={TextButton(onClick=onDismiss){Text("關閉")}})
}
