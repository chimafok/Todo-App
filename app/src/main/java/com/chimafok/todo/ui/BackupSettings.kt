package com.chimafok.todo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupSettings(viewModel:TodoViewModel) {
    val context=LocalContext.current;val files=remember { BackupFiles(context.applicationContext) };val scope=rememberCoroutineScope()
    var token by rememberSaveable { mutableStateOf<String?>(null) }
    var exportToken by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.externalDocumentFlow=false
        if(uri==null){message="已取消匯出";exportToken=null}
        else exportToken?.let { pending -> busy=true;scope.launch {
            try { withContext(Dispatchers.IO){files.export(pending,uri)};message="備份已匯出";error=null;exportToken=null }
            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error="匯出失敗：${e.message ?: "請重新選擇儲存位置"}"}
            finally { busy=false }
        } }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.externalDocumentFlow=false
        if(uri!=null){busy=true;error=null;scope.launch {
            try { val prepared=withContext(Dispatchers.IO){files.import(uri)};token=prepared;message=null }
            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error="無法匯入：${e.message ?: "檔案格式錯誤"}"}
            finally { busy=false }
        } }
    }
    fun export() {
        busy=true;error=null;message=null;scope.launch {
            try {
                val prepared=withContext(Dispatchers.IO){files.stage(viewModel.exportBackup())};exportToken=prepared
                viewModel.externalDocumentFlow=true
                exportLauncher.launch("Todo-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json")
            } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;viewModel.externalDocumentFlow=false;error="匯出失敗：${e.message ?: "請重試"}"}
            finally { busy=false }
        }
    }
    Text("備份與還原",style=MaterialTheme.typography.titleLarge)
    Text("JSON 明文檔案，請選擇合適的儲存位置。包含任務、完成紀錄、重複系列、垃圾桶、分類與標籤排序及一般設定；不含草稿與系統權限。",style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy,onClick=::export,modifier=Modifier.testTag("export_backup")){Text("匯出備份")}
        OutlinedButton(enabled=!busy,onClick={error=null;try{viewModel.externalDocumentFlow=true;importLauncher.launch(arrayOf("*/*"))}
            catch(e:Exception){viewModel.externalDocumentFlow=false;error=e.message ?: "無法開啟檔案選擇器"}},modifier=Modifier.testTag("import_backup")){Text("選擇備份檔")}
    }
    message?.let { Text(it,Modifier.testTag("backup_message")) }
    error?.let { Text(it,Modifier.testTag("backup_error"),color=MaterialTheme.colorScheme.error) }
    if(busy) Text("正在處理檔案…",Modifier.testTag("backup_file_busy"))
    token?.let { session -> RestoreBackupDialog(session,viewModel,files,busy,error ?: message,::export) { token=null } }
}

@Composable
private fun RestoreBackupDialog(token:String,viewModel:TodoViewModel,files:BackupFiles,exportBusy:Boolean,transferStatus:String?,onExport:()->Unit,onDismiss:()->Unit) {
    var mode by rememberSaveable(token) { mutableStateOf(RestoreMode.MERGE.name) }
    var choices by remember(token) { viewModel.backupChoicesFor(token) }
    var acceptedFingerprint by rememberSaveable(token) { mutableStateOf<String?>(null) }
    var epoch by rememberSaveable(token) { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable(token) { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<BackupConflict?>(null) }
    val scope=rememberCoroutineScope()
    fun selected()=choices.mapValues { BackupChoice.valueOf(it.value) }
    LaunchedEffect(token,mode,choices,epoch) {
        busy=true
        try {
            val next=withContext(Dispatchers.IO){viewModel.previewBackup(files.document(token),RestoreMode.valueOf(mode),selected())}
            if(acceptedFingerprint!=null && acceptedFingerprint!=next.localFingerprint){preview=null;error="本機資料已更新，請按重新預覽再確認。"}
            else {acceptedFingerprint=next.localFingerprint;preview=next;error=null}
        } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;preview=null;error=e.message ?: "無法預覽此備份"}
        finally { busy=false }
    }
    Dialog(onDismissRequest={if(!busy && !exportBusy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp).testTag("backup_preview"),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("檢查並還原備份",style=MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(RestoreMode.MERGE to "合併",RestoreMode.REPLACE to "完整取代").forEach { (value,label) ->
                        FilterChip(selected=mode==value.name,enabled=!busy && !exportBusy,label={Text(label)},modifier=Modifier.testTag("backup_mode_${value.name}"),onClick={mode=value.name;choices=emptyMap();acceptedFingerprint=null;preview=null})
                    }
                }
                val p=preview
                LazyColumn(Modifier.weight(1f).testTag("backup_review_list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(if(mode==RestoreMode.REPLACE.name)"完整取代會移除目前所有任務與垃圾桶，再放入檔案資料（含檔案中的垃圾桶），並還原檔案排序及一般設定。舊格式未包含的設定會保留本機值。" else "合併依 ID 及修改時間判斷。較舊備份不會自動蓋過較新的進度；垃圾桶同 ID、系列衝突及不同的一般設定需要明確選擇。")
                        Text("已永久刪除的 ID 可能由舊備份重新加入。匯入的一般完成項會獲得新的保留期限，原完成時間仍保留。垃圾桶沿用原刪除日期，不延長原本的 30 天期限。過去提醒不補發。",style=MaterialTheme.typography.bodySmall)
                    }
                    if(p!=null) {
                        item { Column(Modifier.testTag(if(busy)"backup_recalculating" else "backup_ready"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("本機 ${p.plan.localTaskCount} 項任務；檔案 ${p.plan.fileTaskCount} 項任務。")
                            Text("新增 ${p.plan.added}、更新 ${p.plan.changed}、移除 ${p.plan.removed} 項任務。")
                            Text("結果：${p.plan.graph.rows("categories").size} 個分類、${p.plan.graph.rows("tags").size} 個標籤、${p.plan.graph.rows("recurring_series").size} 個系列。")
                            Text("結果垃圾桶：${p.plan.graph.rows("tasks").count { it["deletedAt"] != null }} 項任務、${p.plan.graph.rows("recurring_series").count { it["deletedAt"] != null }} 個系列。")
                            Text("還原後完成紀錄保留：${p.plan.graph.rows("settings").singleOrNull()?.get("completedRetentionDays")?.let { "$it 天" } ?: "永久"}；分類與標籤排序按選定資料還原。")
                            if(p.plan.sameNames>0)Text("有 ${p.plan.sameNames} 組同名但不同 ID 的分類／標籤，會各自保留。")
                            Text("${p.plan.conflicts.size} 組衝突；每組選擇後才可確認。")
                        } }
                        items(p.plan.conflicts,key={it.id}) { conflict ->
                            OutlinedCard(Modifier.fillMaxWidth().testTag("backup_conflict_${conflict.id}")) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(conflict.label,style=MaterialTheme.typography.titleMedium);Text(conflict.reason)
                                    Text("本機：${backupSideSummary(conflict.local)}",style=MaterialTheme.typography.bodySmall)
                                    Text("檔案：${backupSideSummary(conflict.incoming)}",style=MaterialTheme.typography.bodySmall)
                                    TextButton(onClick={detail=conflict},enabled=!busy){Text("檢視完整明細")}
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        BackupChoice.entries.forEach { choice -> FilterChip(selected=choices[conflict.id]==choice.name,enabled=!busy,
                                            onClick={choices=choices+(conflict.id to choice.name)},label={Text(if(choice==BackupChoice.LOCAL)"保留本機" else "採用檔案")},modifier=Modifier.testTag("backup_choice_${conflict.id}_${choice.name}")) }
                                    }
                                }
                            }
                        }
                    }
                    if(busy)item { Text("正在檢查…") }
                }
                error?.let { Text(it,Modifier.testTag("backup_preview_error"),color=MaterialTheme.colorScheme.error) }
                TextButton(enabled=!busy && !exportBusy,onClick=onExport,modifier=Modifier.testTag("backup_export_before_restore")){Text("先匯出目前資料")}
                transferStatus?.let { Text(it,Modifier.testTag("backup_transfer_status"),style=MaterialTheme.typography.bodySmall) }
                if(error!=null)TextButton(enabled=!busy,onClick={acceptedFingerprint=null;choices=emptyMap();epoch++},modifier=Modifier.testTag("backup_refresh")){Text("重新預覽")}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled=!busy && !exportBusy,onClick=onDismiss,modifier=Modifier.weight(1f).testTag("cancel_backup_restore")){Text("取消")}
                    Button(enabled=!busy && !exportBusy && error==null && p!=null && p.plan.conflicts.all { it.id in choices },modifier=Modifier.weight(1f).testTag("confirm_backup_restore"),onClick={
                        val accepted=preview ?: return@Button;busy=true;error=null
                        scope.launch { try { withContext(Dispatchers.IO){viewModel.applyBackup(files.document(token),accepted,selected())};onDismiss() }
                            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: "還原失敗，原資料保留"}
                            finally {busy=false} }
                    }){Text(if(mode==RestoreMode.REPLACE.name)"確認完整取代" else "確認合併")}
                }
            }
        }
    }
    detail?.let { conflict -> Dialog(onDismissRequest={detail=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(conflict.label,style=MaterialTheme.typography.titleLarge)
                val rows=remember(conflict) { backupDetails("本機",conflict.local)+backupDetails("檔案",conflict.incoming) }
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){items(rows.size){Text(rows[it]());HorizontalDivider()}}
                TextButton(onClick={detail=null}){Text("返回選擇")}
            }
        }
    } }
}
private fun backupTime(value:Any?):String=(value as? Number)?.toLong()?.takeIf { it>0 }?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
} ?: "未知"
private fun backupSideSummary(g:BackupGraph):String = if(g.rows("settings").isNotEmpty())
    "完成紀錄保留：${g.rows("settings").single()["completedRetentionDays"]?.let { "$it 天" } ?: "永久"}"
    else "${g.rows("tasks").size} 項任務、${g.rows("recurring_series").size} 個系列；"+
    (g.rows("tasks").firstOrNull()?.let { "${statusLabel(TaskStatus.valueOf(it.text("status")))}，修改 ${backupTime(it["updatedAt"])}" }
        ?: (g.rows("categories")+g.rows("tags")).firstOrNull()?.let { "${it.text("name")}，修改 ${backupTime(it["updatedAt"])}" } ?: "整組規則與關聯")
private fun backupTaskDetail(r:BackupRow):String = "${r.text("title")}\n${statusLabel(TaskStatus.valueOf(r.text("status")))}"+
    " · 開始 ${r["startDate"] ?: "未填"} ${r["startTime"] ?: ""} ${r["startZoneId"] ?: ""} · 截止 ${r["dueDate"] ?: "未安排"} ${r["dueTime"] ?: ""} ${r["zoneId"] ?: ""}\n修改 ${backupTime(r["updatedAt"])}"+
    (if(r["deletedAt"]!=null)" · 垃圾桶" else "")+"\n${r.text("notes") }"

/** Keep text formatting lazy so opening a large series does not eagerly build every note string. */
private fun backupDetails(source:String,g:BackupGraph):List<()->String> = buildList {
    add { source+"："+backupSideSummary(g) }
    val categories=g.rows("categories").associate { it.text("id") to it.text("name") }
    val tags=g.rows("tags").associate { it.text("id") to it.text("name") }
    val links=g.rows("task_tags").groupBy { it.text("taskId") }
    val custom=g.rows("reminder_overrides").map { it.text("taskId") }.toSet()
    val titles=g.rows("tasks").associate { it.text("id") to it.text("title") }
    g.rows("tasks").forEach { r->add {
        backupTaskDetail(r)+10.toChar()+"分類：${categories[r.text("categoryId")] ?: "未分類"}；標籤："+
            links[r.text("id")].orEmpty().mapNotNull { tags[it.text("tagId")] }.joinToString("、")+
            if(r.text("id") in custom)10.toChar()+"本次提醒為獨立設定" else ""
    } }
    g.rows("recurring_series").forEach { r->add { "${r.text("title")} · 已產生至第 ${r.long("cursor")} 次 · "+when(r["lifecycle"]){"ACTIVE"->"持續重複";"ENDED"->"已結束";"TRASHED"->"垃圾桶";else->"已停止"} } }
    val frequencies=mapOf("DAILY" to "每日","WEEKLY" to "每週","MONTHLY" to "每月","WEEKDAYS" to "指定星期","EVERY_N_DAYS" to "每隔 N 天")
    g.rows("series_rules").forEach { r->add { "規則 ${r.long("version")}，由第 ${r.long("effectiveFrom")} 次起：${frequencies[r.text("frequency")]}，間隔 ${r.long("intervalDays")} 天"+
        10.toChar()+"起日 ${r.text("anchor")} ${r["dueTime"] ?: ""} ${r.text("zoneId")}；結束 ${r["endDate"] ?: r["maxCount"]?.let { "$it 次" } ?: "不限"}"+
        if(r["frequency"]=="WEEKDAYS")"；星期 "+(1..7).filter { r.int("weekdayMask") and (1 shl(it-1))!=0 }.joinToString("、") else "" } }
    g.rows("series_reminders").forEach { r->add { "系列提醒："+(if(r["kind"]=="RELATIVE_DUE")"提前 ${r["offsetMinutes"]} 分鐘" else "每次日期偏移 ${r["dayOffset"]} 天，${r["time"] ?: "依截止時間"}")+"；日期基準 ${r["baseTime"]}"+if(r["enabled"]==true)"（啟用）" else "（停用）" } }
    g.rows("reminders").forEach { r->add { "${titles[r.text("taskId")] ?: "任務"} 的提醒："+(if(r["kind"]=="FIXED")
        Instant.ofEpochMilli(r.long("fixedAt")).atZone(ZoneId.of(r.text("zoneId"))).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS VV"))
        else "提前 ${r["offsetMinutes"]} 分鐘，日期基準 ${r["baseTime"]} ${r["zoneId"]}")+if(r["enabled"]==true)"（啟用）" else "（停用）" } }
    g.rows("occurrences").forEach { r->r.optionalText("snapshot")?.let { json->add {
        val old=OccurrenceSnapshot.read(json);"第 ${r.long("sequence")} 次歷史快照：${old?.title} · ${old?.category} · ${old?.tags?.joinToString("、")}"+10.toChar()+(old?.notes ?: "")
    } } }
    if(g.rows("tasks").isEmpty() && g.rows("recurring_series").isEmpty())(g.rows("categories")+g.rows("tags")).forEach { r->add { "${r.text("name")} · 排序 ${r["sortOrder"] ?: "沿用本機"} · 修改 ${backupTime(r["updatedAt"])}" } }
}
