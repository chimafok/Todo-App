package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.launch
import java.time.Clock

@Composable
fun HistorySettings(state: TodoState, viewModel: TodoViewModel) {
    val current = state.retentionPolicy.days
    var option by rememberSaveable { mutableStateOf(if (current == null) "FOREVER" else if (current in listOf(30,90,365)) current.toString() else "CUSTOM") }
    var custom by rememberSaveable { mutableStateOf(current?.toString() ?: "30") }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var previewDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var previewRevision by rememberSaveable { mutableLongStateOf(0L) }
    var previewTicket by rememberSaveable { mutableStateOf("") }
    val frozenPreview = viewModel.retainedRetention.takeIf { previewTicket == viewModel.retentionTicket }
    val previewTasks = frozenPreview?.tasks.orEmpty()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("完成紀錄保留", style = MaterialTheme.typography.titleLarge)
    Text("目前：${current?.let { "$it 天" } ?: "永久保留"}", Modifier.testTag("retention_current"))
    Text("只處理一般待辦與作業的已完成紀錄。重複紀錄獨立保存。到期先移到垃圾桶，仍可在 30 天內復原。", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("FOREVER" to "永久", "30" to "30 天", "90" to "90 天", "365" to "365 天", "CUSTOM" to "自訂").forEach { (key,label) ->
            FilterChip(option == key, onClick = { option = key; error = null }, enabled = !busy,
                label = { Text(label) }, modifier = Modifier.testTag("retention_$key"))
        }
    }
    if (option == "CUSTOM") OutlinedTextField(custom, { custom = it; error = null }, label = { Text("保留天數") },
        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("retention_days"))
    error?.let { Text(it, Modifier.testTag("retention_error"), color = MaterialTheme.colorScheme.error) }
    Button(enabled = !busy, modifier = Modifier.testTag("preview_retention"), onClick = {
        busy = true; error = null
        scope.launch {
            try {
                val days = if (option == "FOREVER") null else retentionDays(if (option == "CUSTOM") custom else option)
                val preview = viewModel.previewRetention(days)
                previewDays = preview.days; previewRevision = preview.policyRevision
                viewModel.retainedRetention = preview
                previewTicket = java.util.UUID.randomUUID().toString(); viewModel.retentionTicket = previewTicket
                showPreview = true
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
            finally { busy = false }
        }
    }) { Text("預覽並套用") }
    Text("天數從實際完成時計算。復原後重新給予所選保留天數，原完成時間仍保留。背景清理可能受手機省電限制；重新開啟也會檢查。", style = MaterialTheme.typography.bodySmall)
    if (showPreview) AlertDialog(onDismissRequest = { if (!busy) showPreview = false }, modifier = Modifier.testTag("retention_preview"),
        title = { Text("變更完成紀錄保留？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (frozenPreview == null) Text("程式已重新啟動，預覽已失效；請返回修改並重新預覽。", color = MaterialTheme.colorScheme.error)
            Text("保留期限：${previewDays?.let { "$it 天" } ?: "永久"}")
            Text("目前有 ${previewTasks.size} 項已完成任務會移到垃圾桶，從移入時再保留 30 天。重複歷史及未完成任務會保留。")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy && frozenPreview != null, modifier = Modifier.testTag("confirm_retention"), onClick = {
            busy = true; error = null
            scope.launch { try { viewModel.applyRetention(requireNotNull(frozenPreview)); showPreview = false; viewModel.retainedRetention = null }
                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                finally { busy = false } }
        }) { Text("確認套用") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { showPreview = false; error = null }, modifier = Modifier.testTag("cancel_retention")) { Text("返回修改") } })
}

@Composable
fun PurgeDialog(preview: PurgePreview, viewModel: TodoViewModel, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, modifier = Modifier.testTag("purge_confirmation"),
        title = { Text(if (preview.allTrash) "清空全部垃圾桶？" else "永久刪除這項任務？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("將永久刪除 ${preview.tasks.size} 項任務" + if (preview.series.isNotEmpty()) "與 ${preview.series.size} 個已刪除的系列。" else "。")
            if (preview.allTrash) Text("包含搜尋及篩選未顯示的全部垃圾桶資料。")
            Text("刪除後無法復原，相關標籤關聯與提醒也會移除。其他紀錄及有效的系列規則保留。")
            error?.let { Text(it, Modifier.testTag("purge_error"), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm_purge"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = {
            busy = true; error = null
            scope.launch { try { viewModel.purge(preview); onDismiss() }
                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                finally { busy = false } }
        }) { Text("永久刪除") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("cancel_purge")) { Text("取消") } })
}

@Composable
fun TrashToolbar(state: TodoState, viewModel: TodoViewModel, clock: Clock, enabled: Boolean, onEmpty: () -> Unit,
    selectionMode:Boolean=false,selectedSeries:Set<String> = emptySet(),onToggleSeries:(String)->Unit={},
    onLongSeries:((String)->Unit)?=null,onSelectAllSeries:()->Unit={}) {
    val groups = state.series.filter { it.deletedAt != null }
    val remainingCounts=remember(state.series,state.tasks,state.occurrences) {
        val byTask=state.tasks.associateBy {it.id};val bySeries=state.occurrences.groupBy {it.seriesId}
        groups.associate { series->series.id to bySeries[series.id].orEmpty().count {ref->ref.deletedWithSeriesAt==series.deletedAt && byTask[ref.taskId]?.deletedAt==series.deletedAt} }
    }
    val tasks = state.tasks.count { it.deletedAt != null }
    var restoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by rememberSaveable { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("30 天後自動永久刪除", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        TextButton(enabled = enabled && !selectionMode && (tasks > 0 || groups.isNotEmpty()), onClick = onEmpty, modifier = Modifier.testTag("empty_trash")) { Text("清空全部…") }
    }
    if (groups.isNotEmpty()) Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if(selectionMode)TextButton(enabled=enabled,onClick=onSelectAllSeries,modifier=Modifier.testTag("select_all_trash_series")){Text("全選已刪系列")}
        groups.forEach { series -> Row(Modifier.fillMaxWidth().testTag("trash_series_${series.id}").combinedClickable(
            enabled=enabled && onLongSeries!=null,onClick={if(selectionMode)onToggleSeries(series.id) else onLongSeries?.invoke(series.id)},
            onLongClick={onLongSeries?.invoke(series.id)},onLongClickLabel="選取系列"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(selectionMode)Checkbox(series.id in selectedSeries,onCheckedChange={onToggleSeries(series.id)},enabled=enabled,modifier=Modifier.testTag("select_trash_series_${series.id}"))
            val remaining=remainingCounts[series.id] ?: 0
            Text("${series.title} · 剩餘 $remaining 項", Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = enabled && !selectionMode && !retentionExpired(requireNotNull(series.deletedAt),TRASH_DAYS,clock.millis()), modifier = Modifier.testTag("trash_restore_series_${series.id}"), onClick = {
                restoreId = series.id; revision = series.revision; error = null
            }) { Text("復原系列") }
        } }
    }
    restoreId?.let { id -> state.series.find { it.id == id }?.let { series ->
        val remaining=remainingCounts[id] ?: 0
        AlertDialog(onDismissRequest = { if (!busy) restoreId = null }, title = { Text("復原重複系列？") },
            text = { Column { Text("「${series.title}」與剩餘 $remaining 項同次刪除的紀錄會復原。已永久刪除的紀錄無法復原；先前單獨刪除的項目保留在垃圾桶。"); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm_trash_restore_series"), onClick = {
                busy = true; scope.launch { try { viewModel.restoreSeries(id, revision); restoreId = null }
                    catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
                    finally { busy = false } }
            }) { Text("復原剩餘資料") } }, dismissButton = { TextButton(enabled = !busy, onClick = { restoreId = null }) { Text("取消") } })
    } }
}
