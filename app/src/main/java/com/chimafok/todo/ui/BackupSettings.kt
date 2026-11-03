package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
        if(uri==null){message=tr(R.string.text_export_canceled_ec28ff);exportToken=null}
        else exportToken?.let { pending -> busy=true;scope.launch {
            try { withContext(Dispatchers.IO){files.export(pending,uri)};message=tr(R.string.text_backup_exported_61b8a6);error=null;exportToken=null }
            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=tr(R.string.text_export_failed_s_949334, e.message ?: tr(R.string.text_choose_the_save_location_again_1e8423))}
            finally { busy=false }
        } }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.externalDocumentFlow=false
        if(uri!=null){busy=true;error=null;scope.launch {
            try { val prepared=withContext(Dispatchers.IO){files.import(uri)};token=prepared;message=null }
            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=tr(R.string.text_cannot_import_s_aeb4c5, e.message ?: tr(R.string.text_invalid_file_format_a2cf55))}
            finally { busy=false }
        } }
    }
    fun export() {
        busy=true;error=null;message=null;scope.launch {
            try {
                val prepared=withContext(Dispatchers.IO){files.stage(viewModel.exportBackup())};exportToken=prepared
                viewModel.externalDocumentFlow=true
                exportLauncher.launch("Todo-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json")
            } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;viewModel.externalDocumentFlow=false;error=tr(R.string.text_export_failed_s_949334, e.message ?: tr(R.string.text_please_try_again_1721bd))}
            finally { busy=false }
        }
    }
    Text(tr(R.string.text_backup_and_restore_ee1dad),style=MaterialTheme.typography.titleLarge)
    Text(tr(R.string.text_unencrypted_json_file_choose_an_appropriate_71f2dd),style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy,onClick=::export,modifier=Modifier.testTag("export_backup")){Text(tr(R.string.text_export_backup_b5a254))}
        OutlinedButton(enabled=!busy,onClick={error=null;try{viewModel.externalDocumentFlow=true;importLauncher.launch(arrayOf("*/*"))}
            catch(e:Exception){viewModel.externalDocumentFlow=false;error=e.message ?: tr(R.string.text_cannot_open_the_file_picker_beffea)}},modifier=Modifier.testTag("import_backup")){Text(tr(R.string.text_choose_backup_file_f95ea6))}
    }
    message?.let { Text(it,Modifier.testTag("backup_message")) }
    error?.let { Text(it,Modifier.testTag("backup_error"),color=MaterialTheme.colorScheme.error) }
    if(busy) Text(tr(R.string.text_processing_file_352207),Modifier.testTag("backup_file_busy"))
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
            if(acceptedFingerprint!=null && acceptedFingerprint!=next.localFingerprint){preview=null;error=tr(R.string.text_local_data_changed_preview_again_before_b13126)}
            else {acceptedFingerprint=next.localFingerprint;preview=next;error=null}
        } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;preview=null;error=e.message ?: tr(R.string.text_cannot_preview_this_backup_78b182)}
        finally { busy=false }
    }
    Dialog(onDismissRequest={if(!busy && !exportBusy)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp).testTag("backup_preview"),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(tr(R.string.text_review_and_restore_backup_b18649),style=MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(RestoreMode.MERGE to tr(R.string.text_merge_818474),RestoreMode.REPLACE to tr(R.string.text_replace_all_68b101)).forEach { (value,label) ->
                        FilterChip(selected=mode==value.name,enabled=!busy && !exportBusy,label={Text(label)},modifier=Modifier.testTag("backup_mode_${value.name}"),onClick={mode=value.name;choices=emptyMap();acceptedFingerprint=null;preview=null})
                    }
                }
                val p=preview
                LazyColumn(Modifier.weight(1f).testTag("backup_review_list"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(if(mode==RestoreMode.REPLACE.name)tr(R.string.text_replace_all_removes_current_tasks_and_25a175) else tr(R.string.text_merge_compares_ids_and_modification_times_94b362))
                        Text(tr(R.string.text_permanently_deleted_ids_may_return_from_a82c29),style=MaterialTheme.typography.bodySmall)
                    }
                    if(p!=null) {
                        item { Column(Modifier.testTag(if(busy)"backup_recalculating" else "backup_ready"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(tr(R.string.text_local_s_tasks_file_s_tasks_d4cce0, p.plan.localTaskCount, p.plan.fileTaskCount))
                            Text(tr(R.string.text_add_s_update_s_remove_s_3d41f3, p.plan.added, p.plan.changed, p.plan.removed))
                            Text(tr(R.string.text_result_s_categories_s_tags_s_72d7eb, p.plan.graph.rows("categories").size, p.plan.graph.rows("tags").size, p.plan.graph.rows("recurring_series").size))
                            Text(tr(R.string.text_result_s_images_attachments_are_restored_66d62d, p.plan.graph.rows("note_images").size))
                            Text(tr(R.string.text_result_in_trash_s_tasks_s_6f433b, p.plan.graph.rows("tasks").count { it["deletedAt"] != null }, p.plan.graph.rows("recurring_series").count { it["deletedAt"] != null }))
                            Text(tr(R.string.text_completed_record_retention_after_restore_s_26f607, p.plan.graph.rows("settings").singleOrNull()?.get("completedRetentionDays")?.let { tr(R.string.text_s_days_5c0556, it) } ?: tr(R.string.text_forever_3e71cc)))
                            Text(tr(R.string.restored_language, com.chimafok.todo.i18n.languageName(p.plan.graph.rows("settings").singleOrNull()?.optionalText("language"))))
                            if(p.plan.sameNames>0)Text(tr(R.string.text_s_groups_of_categories_or_tags_1d5522, p.plan.sameNames))
                            Text(tr(R.string.text_s_conflicts_choose_a_version_for_20aecd, p.plan.conflicts.size))
                        } }
                        items(p.plan.conflicts,key={it.id}) { conflict ->
                            OutlinedCard(Modifier.fillMaxWidth().testTag("backup_conflict_${conflict.id}")) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(conflict.label,style=MaterialTheme.typography.titleMedium);Text(conflict.reason)
                                    Text(tr(R.string.text_local_s_b3905e, backupSideSummary(conflict.local)),style=MaterialTheme.typography.bodySmall)
                                    Text(tr(R.string.text_file_s_c1de99, backupSideSummary(conflict.incoming)),style=MaterialTheme.typography.bodySmall)
                                    TextButton(onClick={detail=conflict},enabled=!busy){Text(tr(R.string.text_view_full_details_6bdc9a))}
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        BackupChoice.entries.forEach { choice -> FilterChip(selected=choices[conflict.id]==choice.name,enabled=!busy,
                                            onClick={choices=choices+(conflict.id to choice.name)},label={Text(if(choice==BackupChoice.LOCAL)tr(R.string.text_keep_local_1d2b63) else tr(R.string.text_use_file_7d6f4c))},modifier=Modifier.testTag("backup_choice_${conflict.id}_${choice.name}")) }
                                    }
                                }
                            }
                        }
                    }
                    if(busy)item { Text(tr(R.string.text_checking_15ad10)) }
                }
                error?.let { Text(it,Modifier.testTag("backup_preview_error"),color=MaterialTheme.colorScheme.error) }
                TextButton(enabled=!busy && !exportBusy,onClick=onExport,modifier=Modifier.testTag("backup_export_before_restore")){Text(tr(R.string.text_export_current_data_first_494167))}
                transferStatus?.let { Text(it,Modifier.testTag("backup_transfer_status"),style=MaterialTheme.typography.bodySmall) }
                if(error!=null)TextButton(enabled=!busy,onClick={acceptedFingerprint=null;choices=emptyMap();epoch++},modifier=Modifier.testTag("backup_refresh")){Text(tr(R.string.text_preview_again_bc05f4))}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled=!busy && !exportBusy,onClick=onDismiss,modifier=Modifier.weight(1f).testTag("cancel_backup_restore")){Text(tr(R.string.text_cancel_2cd0f3))}
                    Button(enabled=!busy && !exportBusy && error==null && p!=null && p.plan.conflicts.all { it.id in choices },modifier=Modifier.weight(1f).testTag("confirm_backup_restore"),onClick={
                        val accepted=preview ?: return@Button;busy=true;error=null
                        scope.launch { try { withContext(Dispatchers.IO){viewModel.applyBackup(files.document(token),accepted,selected())};onDismiss() }
                            catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message ?: tr(R.string.text_restore_failed_original_data_kept_5484c4)}
                            finally {busy=false} }
                    }){Text(if(mode==RestoreMode.REPLACE.name)tr(R.string.text_confirm_replacement_bf11ba) else tr(R.string.text_confirm_merge_b44627))}
                }
            }
        }
    }
    detail?.let { conflict -> Dialog(onDismissRequest={detail=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(conflict.label,style=MaterialTheme.typography.titleLarge)
                val rows=remember(conflict) { backupDetails(tr(R.string.text_local_019e29),conflict.local)+backupDetails(tr(R.string.text_file_a11ac5),conflict.incoming) }
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){items(rows.size){Text(rows[it]());HorizontalDivider()}}
                TextButton(onClick={detail=null}){Text(tr(R.string.text_back_to_choices_0fed12))}
            }
        }
    } }
}
private fun backupTime(value:Any?):String=(value as? Number)?.toLong()?.takeIf { it>0 }?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
} ?: tr(R.string.text_unknown_4d8c1c)
private fun backupSideSummary(g:BackupGraph):String = if(g.rows("settings").isNotEmpty())
    tr(R.string.text_completed_record_retention_s_a4cf43, g.rows("settings").single()["completedRetentionDays"]?.let { tr(R.string.text_s_days_5c0556, it) } ?: tr(R.string.text_forever_3e71cc)) +
        " · " + tr(R.string.language_summary, com.chimafok.todo.i18n.languageName(g.rows("settings").single().optionalText("language")))
    else tr(R.string.text_s_tasks_s_series_s_images_728f14, g.rows("tasks").size, g.rows("recurring_series").size, referencedImageIds(g).size)+
    (g.rows("tasks").firstOrNull()?.let { tr(R.string.text_s_modified_s_36aeef, statusLabel(TaskStatus.valueOf(it.text("status")), it.optionalText("categoryId")), backupTime(it["updatedAt"])) }
        ?: (g.rows("categories")+g.rows("tags")).firstOrNull()?.let { tr(R.string.text_s_modified_s_36aeef, it.text("name"), backupTime(it["updatedAt"])) } ?: tr(R.string.text_full_rules_and_relationships_bacb09))
private fun backupTaskDetail(r:BackupRow):String = "${r.text("title")}\n${statusLabel(TaskStatus.valueOf(r.text("status")), r.optionalText("categoryId"))}"+
    tr(R.string.text_start_s_s_s_due_s_dcb045, r["startDate"] ?: tr(R.string.text_not_set_64747f), r["startTime"] ?: "", r["startZoneId"] ?: "", r["dueDate"] ?: tr(R.string.text_unscheduled_7c525e), r["dueTime"] ?: "", r["zoneId"] ?: "", backupTime(r["updatedAt"]))+
    (if(r["deletedAt"]!=null)tr(R.string.text_trash_5929cf) else "")+tr(R.string.text_s_images_s_19e0a8, com.chimafok.todo.domain.decodeImageIds(r.optionalText("imageIds").orEmpty()).size, r.text("notes") )

/** Keep text formatting lazy so opening a large series does not eagerly build every note string. */
private fun backupDetails(source:String,g:BackupGraph):List<()->String> = buildList {
    add { source+"："+backupSideSummary(g) }
    val categories=g.rows("categories").associate { it.text("id") to it.text("name") }
    val tags=g.rows("tags").associate { it.text("id") to it.text("name") }
    val links=g.rows("task_tags").groupBy { it.text("taskId") }
    val custom=g.rows("reminder_overrides").map { it.text("taskId") }.toSet()
    val titles=g.rows("tasks").associate { it.text("id") to it.text("title") }
    g.rows("tasks").forEach { r->add {
        backupTaskDetail(r)+10.toChar()+tr(R.string.text_category_s_tags_64b07a, categories[r.text("categoryId")] ?: tr(R.string.text_uncategorized_607089))+
            links[r.text("id")].orEmpty().mapNotNull { tags[it.text("tagId")] }.joinToString("、")+
            if(r.text("id") in custom)10.toChar()+tr(R.string.text_this_occurrence_uses_separate_reminders_2efaea) else ""
    } }
    g.rows("recurring_series").forEach { r->add { tr(R.string.text_s_generated_through_occurrence_s_a68830, r.text("title"), r.long("cursor"))+when(r["lifecycle"]){"ACTIVE"->tr(R.string.text_recurring_0ee582);"ENDED"->tr(R.string.text_ended_eea860);"TRASHED"->tr(R.string.text_trash_d7b740);else->tr(R.string.text_stopped_f00645)} } }
    val frequencies=mapOf("DAILY" to tr(R.string.text_daily_fd7b67),"WEEKLY" to tr(R.string.text_weekly_a8a3df),"MONTHLY" to tr(R.string.text_monthly_68b21a),"WEEKDAYS" to tr(R.string.text_selected_weekdays_990286),"EVERY_N_DAYS" to tr(R.string.text_every_n_days_9e9e21))
    g.rows("series_rules").forEach { r->add { tr(R.string.text_rule_s_from_occurrence_s_s_5b986f, r.long("version"), r.long("effectiveFrom"), frequencies[r.text("frequency")], r.long("intervalDays"))+
        10.toChar()+tr(R.string.text_first_date_s_s_s_end_e0a3c3, r.text("anchor"), r["dueTime"] ?: "", r.text("zoneId"), r["endDate"] ?: r["maxCount"]?.let { tr(R.string.text_s_occurrences_38fe0e, it) } ?: tr(R.string.text_no_limit_f203d5))+
        if(r["frequency"]=="WEEKDAYS")tr(R.string.text_weekdays_1490cc)+(1..7).filter { r.int("weekdayMask") and (1 shl(it-1))!=0 }.joinToString("、") else "" } }
    g.rows("series_reminders").forEach { r->add { tr(R.string.text_series_reminders_c647b1)+(if(r["kind"]=="RELATIVE_DUE")tr(R.string.text_s_minutes_before_due_8b49eb, r["offsetMinutes"]) else tr(R.string.text_each_occurrence_offset_s_days_s_c05302, r["dayOffset"], r["time"] ?: tr(R.string.text_use_due_time_9a3aa3)))+tr(R.string.text_date_only_baseline_s_f21ed4, r["baseTime"])+if(r["enabled"]==true)tr(R.string.text_enabled_a09385) else tr(R.string.text_disabled_6f2390) } }
    g.rows("reminders").forEach { r->add { tr(R.string.text_reminders_for_s_9c1015, titles[r.text("taskId")] ?: tr(R.string.text_task_c72c77))+(if(r["kind"]=="FIXED")
        Instant.ofEpochMilli(r.long("fixedAt")).atZone(ZoneId.of(r.text("zoneId"))).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS VV"))
        else tr(R.string.text_s_minutes_before_due_date_only_caac86, r["offsetMinutes"], r["baseTime"], r["zoneId"]))+if(r["enabled"]==true)tr(R.string.text_enabled_a09385) else tr(R.string.text_disabled_6f2390) } }
    g.rows("occurrences").forEach { r->r.optionalText("snapshot")?.let { json->add {
        val old=OccurrenceSnapshot.read(json);tr(R.string.text_snapshot_of_occurrence_s_s_s_8b431b, r.long("sequence"), old?.title, old?.category, old?.tags?.joinToString("、"))+10.toChar()+(old?.notes ?: "")
    } } }
    if(g.rows("tasks").isEmpty() && g.rows("recurring_series").isEmpty())(g.rows("categories")+g.rows("tags")).forEach { r->add { tr(R.string.text_s_order_s_modified_s_8620e3, r.text("name"), r["sortOrder"] ?: tr(R.string.text_keep_local_value_856df0), backupTime(r["updatedAt"])) } }
}
