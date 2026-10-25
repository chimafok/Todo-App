package com.chimafok.todo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chimafok.todo.R
import com.chimafok.todo.data.ReminderEntity
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Primitive JSON keeps incomplete text, IDs, captured zone and reference across recreation. */
internal data class ReminderInput(val id: String = UUID.randomUUID().toString(), val kind: String = "FIXED",
    val date: String = "", val time: String = "", val offset: String = "1", val base: String = "10:00",
    val zone: String = ZoneId.systemDefault().id, val reference: Long = System.currentTimeMillis(),
    val originalFixedAt: Long? = null, val offsetUnit: ReminderOffsetUnit = ReminderOffsetUnit.DAYS) {
    fun effectiveTime(dueDate: LocalDate?, dueTime: LocalTime?, deadlineZone: String?): LocalTime =
        parseTimeInput(time) ?: fixedReminderDefaultTime(dueDate, dueTime, deadlineZone, zone,
            if (dueTime == null) requireNotNull(parseTimeInput(base)) { "請填只有日期時的提醒基準" } else DEFAULT_REMINDER_TIME)
    fun spec(dueDate: LocalDate? = null, dueTime: LocalTime? = null, deadlineZone: String? = null): ReminderSpec {
        val z = ZoneId.of(zone)
        require(kind == "FIXED" || kind == "RELATIVE_DUE") { "提醒類型無效" }
        return if (kind == "FIXED") {
            val t = effectiveTime(dueDate, dueTime, deadlineZone)
            val d = requireNotNull(resolveDeadlineDate(date, t, zone, Instant.ofEpochMilli(reference))) { "請填固定提醒日期" }
            val preserved = originalFixedAt?.takeIf {
                val original = Instant.ofEpochMilli(it).atZone(z)
                time.isNotBlank() && original.toLocalDate() == d && original.toLocalTime().withSecond(0).withNano(0) == t
            }
            ReminderSpec(id, ReminderKind.FIXED, zone, fixedAt = preserved ?: deadlineInstant(d, t, zone).toEpochMilli(),
                baseTime = runCatching { parseTimeInput(base) }.getOrNull() ?: DEFAULT_REMINDER_TIME)
        } else ReminderSpec(id, ReminderKind.RELATIVE_DUE, zone,
            offsetMinutes = reminderOffsetMinutes(offset, offsetUnit),
            baseTime = requireNotNull(parseTimeInput(base)) { "請填只有日期時的提醒基準" })
    }
    fun json() = JSONObject().put("id", id).put("kind", kind).put("date", date).put("time", time)
        .put("offset", offset).put("offsetUnit", offsetUnit.name).put("base", base).put("zone", zone)
        .put("reference", reference).put("originalFixedAt", originalFixedAt ?: JSONObject.NULL)
}
internal fun encodeReminderInputs(inputs: List<ReminderInput>) = JSONArray(inputs.map { it.json() }).toString()
internal fun decodeReminderInputs(json: String): List<ReminderInput> {
    val array = JSONArray(json)
    return (0 until array.length()).map { array.getJSONObject(it) }.map {
        // Drafts saved by 0.5.0 stored raw minutes without a unit.
        val old = if (!it.has("offsetUnit")) it.getString("offset").toIntOrNull()?.let(::reminderOffsetDisplay) else null
        ReminderInput(it.getString("id"), it.getString("kind"), it.getString("date"), it.getString("time"),
            old?.first ?: it.getString("offset"), it.getString("base"), it.getString("zone"), it.getLong("reference"),
            if (it.isNull("originalFixedAt")) null else it.getLong("originalFixedAt"),
            if (it.has("offsetUnit")) ReminderOffsetUnit.valueOf(it.getString("offsetUnit")) else old?.second ?: ReminderOffsetUnit.MINUTES)
    }
}
internal fun ReminderEntity.input(): ReminderInput = spec().input()
internal fun ReminderSpec.input(): ReminderInput {
    val fixed = fixedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(zoneId)) }
    val offset = reminderOffsetDisplay(offsetMinutes ?: 1440)
    return ReminderInput(id, kind.name, fixed?.toLocalDate()?.toString().orEmpty(),
        fixed?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
        offset.first, baseTime.toString(), zoneId, originalFixedAt = fixedAt, offsetUnit = offset.second)
}

@Composable
fun ReminderCapabilities(onFirstUse: (() -> Unit)? = null) {
    val context = LocalContext.current
    val gateway = remember(context) { AndroidReminderGateway(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            refresh++; ReminderRuntime.refresh(context)
        } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++; ReminderRuntime.refresh(context)
    }
    val failure by ReminderRuntime.failed.collectAsState()
    if (failure) {
        Text("提醒排程尚未完成，設定已保留。", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { ReminderRuntime.refresh(context) }) { Text("重新檢查提醒") }
    }
    val allowed = remember(refresh) { gateway.notificationsAllowed() }
    val exact = remember(refresh) { gateway.exactAllowed() }
    Text(if (!allowed) "通知尚未開啟；設定仍可儲存，開啟後才會發出提醒。"
        else if (!exact) "目前使用一般提醒，可能延遲。可開啟「鬧鐘與提醒」提高準時程度。"
        else "通知與精準提醒已開啟；仍可能受系統背景及省電限制影響。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("reminder_capability"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!allowed) TextButton(onClick = {
            gateway.ensureChannel()
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else gateway.openNotifications()
        }, modifier = Modifier.testTag("enable_notifications")) { Text("開啟通知") }
        if (!exact) TextButton(onClick = { gateway.openExact() }) { Text("鬧鐘與提醒設定") }
        if (onFirstUse != null) TextButton(onClick = onFirstUse) { Text("繼續設定提醒") }
    }
}

@Composable
internal fun ReminderFields(encoded: String, onChange: (String) -> Unit, dueDate: LocalDate?, dueTime: LocalTime?,
    deadlineZone: String?, saved: List<ReminderEntity>, enabled: Boolean, allowAdd: Boolean) {
    val rows = decodeReminderInputs(encoded)
    fun replace(index: Int, row: ReminderInput) { onChange(encodeReminderInputs(rows.toMutableList().also { it[index] = row })) }
    Text("提醒", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    if (!allowAdd) Text("此開發版先支援單筆任務提醒，重複系列提醒將在下一批加入。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (rows.isNotEmpty()) ReminderCapabilities()
    rows.forEachIndexed { index, row -> key(row.id) {
        OutlinedCard(Modifier.fillMaxWidth().testTag("reminder_row_$index")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("提醒 ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = enabled, onClick = { onChange(encodeReminderInputs(rows.filter { it.id != row.id })) },
                        modifier = Modifier.testTag("remove_reminder_$index")) { Text("移除") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(row.kind == "FIXED", enabled = enabled, onClick = { replace(index, row.copy(kind = "FIXED")) },
                        label = { Text("固定時間") }, modifier = Modifier.testTag("reminder_fixed_$index"))
                    FilterChip(row.kind == "RELATIVE_DUE", enabled = enabled, onClick = { replace(index, row.copy(kind = "RELATIVE_DUE")) },
                        label = { Text("截止前") }, modifier = Modifier.testTag("reminder_relative_$index"))
                }
                if (row.kind == "FIXED") {
                    OutlinedTextField(row.date, { replace(index, row.copy(date = it, zone = ZoneId.systemDefault().id, reference = System.currentTimeMillis())) },
                        label = { Text("提醒日期") }, placeholder = { Text("YYYY-MM-DD") }, enabled = enabled, singleLine = true,
                        trailingIcon = { DatePickerButton(runCatching { LocalDate.parse(row.date) }.getOrNull() ?: LocalDate.now(), R.string.choose_date, enabled) {
                            replace(index, row.copy(date = it.toString(), zone = ZoneId.systemDefault().id, reference = System.currentTimeMillis()))
                        } }, modifier = Modifier.fillMaxWidth().testTag("reminder_date_$index"))
                    var picker by rememberSaveable { mutableStateOf(false) }
                    OutlinedTextField(row.time, { replace(index, row.copy(time = it, zone = ZoneId.systemDefault().id, reference = System.currentTimeMillis())) },
                        label = { Text("提醒時間（選填）") }, placeholder = { Text("HH:mm") }, enabled = enabled, singleLine = true,
                        trailingIcon = { TextButton(enabled = enabled, onClick = { picker = true }) { Text("選擇") } },
                        modifier = Modifier.fillMaxWidth().testTag("reminder_time_$index"))
                    if (picker) TodoTimePicker(runCatching { row.effectiveTime(dueDate, dueTime, deadlineZone) }.getOrNull() ?: DEFAULT_REMINDER_TIME,
                        onDismiss = { picker = false }, onTime = { replace(index, row.copy(time = it.toString(), zone = ZoneId.systemDefault().id,
                            reference = System.currentTimeMillis())); picker = false })
                    Text(if (row.time.isBlank()) "時間留空：採用" + (if (dueTime != null) "截止時間（換算至此提醒時區）" else "只有日期時的提醒基準") + "，儲存後固定。"
                        else "儲存後為固定時刻，不隨截止變更。", style = MaterialTheme.typography.bodySmall)
                    Text("固定時區：${row.zone}", style = MaterialTheme.typography.bodySmall)
                } else {
                    var unitMenu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(row.offset, { replace(index, row.copy(offset = it)) }, label = { Text("提前數字") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = enabled, singleLine = true, modifier = Modifier.weight(1f).testTag("reminder_offset_$index"))
                        Box(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(enabled = enabled, onClick = { unitMenu = true },
                                modifier = Modifier.heightIn(min = 56.dp).testTag("reminder_unit_$index")) { Text("${row.offsetUnit.label} ▾") }
                            DropdownMenu(expanded = unitMenu && enabled, onDismissRequest = { unitMenu = false }) {
                                ReminderOffsetUnit.entries.forEach { unit -> DropdownMenuItem(text = { Text(unit.label) },
                                    onClick = { replace(index, row.copy(offsetUnit = unit)); unitMenu = false },
                                    modifier = Modifier.testTag("reminder_unit_${index}_${unit.name}")) }
                            }
                        }
                    }
                    Text("0 表示截止時；最多提前 3650 天。", style = MaterialTheme.typography.bodySmall)
                }
                if (dueTime == null && (row.kind == "RELATIVE_DUE" || row.time.isBlank())) {
                    OutlinedTextField(row.base, { replace(index, row.copy(base = it)) }, label = { Text("只有日期時的提醒基準") },
                        placeholder = { Text("HH:mm") }, enabled = enabled, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reminder_base_$index"))
                    Text("只用來計算提醒，不會替任務填入截止時間。", style = MaterialTheme.typography.bodySmall)
                }
                val preview = runCatching { resolveReminder(row.spec(dueDate, dueTime, deadlineZone), dueDate, dueTime, deadlineZone) }
                Text(preview.fold(onSuccess = { "提醒時間：${reminderInstantLabel(it)}" + if (it <= System.currentTimeMillis()) "（已過，不補發）" else "" },
                    onFailure = { it.message ?: "請填完整提醒" }), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("reminder_preview_$index"))
                saved.find { it.id == row.id }?.let { Text("目前紀錄：${reminderStateLabel(it.state)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    } }
    if (allowAdd && rows.size < 32) OutlinedButton(onClick = {
        onChange(encodeReminderInputs(rows + ReminderInput(date = (dueDate ?: LocalDate.now()).toString())))
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("add_reminder")) { Text("＋ 新增提醒") }
}
