package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.reminderInstantLabel
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

internal data class SeriesReminderInput(val id: String = UUID.randomUUID().toString(), val kind: String = "RELATIVE_DUE",
    val quantity: String = "1", val unit: String = "DAYS", val dayCount: String = "1", val direction: String = "ON",
    val time: String = "", val base: String = "10:00") {
    fun spec(): SeriesReminderSpec {
        val type = SeriesReminderKind.valueOf(kind)
        val baseline = requireNotNull(parseTimeInput(base)) { "請填只有日期時的提醒基準" }
        return if (type == SeriesReminderKind.RELATIVE_DUE) SeriesReminderSpec(id, type,
            offsetMinutes = reminderOffsetMinutes(quantity, ReminderOffsetUnit.valueOf(unit)), baseTime = baseline)
        else {
            require(direction in listOf("BEFORE", "ON", "AFTER")) { "日期方向無效" }
            val days = if (direction == "ON") 0 else {
                require(dayCount.trim().matches(Regex("[0-9]+"))) { "請填有效的天數" }
                requireNotNull(dayCount.trim().toIntOrNull()) { "天數超出範圍" }.also { require(it in 0..3650) { "日期偏移不可超過 3650 天" } }
            }
            SeriesReminderSpec(id, type, dayOffset = if (direction == "BEFORE") -days else days,
                time = parseTimeInput(time), baseTime = baseline)
        }
    }
    fun json() = JSONObject().put("id",id).put("kind",kind).put("quantity",quantity).put("unit",unit)
        .put("dayCount",dayCount).put("direction",direction).put("time",time).put("base",base)
}
internal fun encodeSeriesReminderInputs(rows: List<SeriesReminderInput>) = JSONArray(rows.map { it.json() }).toString()
internal fun decodeSeriesReminderInputs(json: String): List<SeriesReminderInput> {
    val array = JSONArray(json)
    return (0 until array.length()).map { array.getJSONObject(it) }.map { SeriesReminderInput(it.getString("id"), it.getString("kind"),
        it.getString("quantity"), it.getString("unit"), it.getString("dayCount"), it.getString("direction"),it.getString("time"),it.getString("base")) }
}
internal fun SeriesReminderSpec.input(): SeriesReminderInput {
    val offset = reminderOffsetDisplay(offsetMinutes ?: 1440)
    return SeriesReminderInput(id, kind.name, offset.first, offset.second.name, kotlin.math.abs(dayOffset ?: 1).toString(),
        if (dayOffset == null || dayOffset == 0) "ON" else if (dayOffset < 0) "BEFORE" else "AFTER",
        time?.toString().orEmpty(), baseTime.toString())
}

@Composable
internal fun SeriesReminderFields(encoded: String, onChange: (String) -> Unit, date: LocalDate?, time: LocalTime?, zone: String?, enabled: Boolean) {
    val rows = decodeSeriesReminderInputs(encoded)
    fun replace(index: Int, row: SeriesReminderInput) { onChange(encodeSeriesReminderInputs(rows.toMutableList().also { it[index] = row })) }
    Text("系列提醒", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text("套用於每一次。每次時刻按該次發生日計算，不會重用第一筆的固定日期。", style = MaterialTheme.typography.bodySmall)
    if (rows.isNotEmpty()) ReminderCapabilities()
    rows.forEachIndexed { index, row -> key(row.id) {
        OutlinedCard(Modifier.fillMaxWidth().testTag("series_reminder_row_$index")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("提醒 ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = enabled, onClick = { onChange(encodeSeriesReminderInputs(rows.filterNot { it.id == row.id })) },
                        modifier = Modifier.testTag("remove_series_reminder_$index")) { Text("移除") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(row.kind == "RELATIVE_DUE", enabled = enabled, onClick = { replace(index,row.copy(kind="RELATIVE_DUE")) },
                        label = { Text("截止前") }, modifier = Modifier.testTag("series_reminder_relative_$index"))
                    FilterChip(row.kind == "OCCURRENCE_TIME", enabled = enabled, onClick = { replace(index,row.copy(kind="OCCURRENCE_TIME")) },
                        label = { Text("每次時刻") }, modifier = Modifier.testTag("series_reminder_fixed_$index"))
                }
                if (row.kind == "RELATIVE_DUE") {
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(row.quantity, { replace(index,row.copy(quantity=it)) }, label = { Text("提前數字") }, enabled=enabled,
                            singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number), modifier=Modifier.weight(1f).testTag("series_reminder_quantity_$index"))
                        Box(Modifier.padding(top=8.dp)) {
                            OutlinedButton(onClick={menu=true}, enabled=enabled, modifier=Modifier.heightIn(min = 56.dp).testTag("series_reminder_unit_$index")) {
                                Text("${ReminderOffsetUnit.valueOf(row.unit).label} ▾")
                            }
                            DropdownMenu(menu && enabled, onDismissRequest={menu=false}) {
                                ReminderOffsetUnit.entries.forEach { unit -> DropdownMenuItem(text={Text(unit.label)},
                                    onClick={replace(index,row.copy(unit=unit.name));menu=false}, modifier=Modifier.testTag("series_reminder_unit_${index}_${unit.name}")) }
                            }
                        }
                    }
                } else {
                    Text("每次發生日",style=MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("BEFORE" to "之前", "ON" to "當天", "AFTER" to "之後").forEach { (value,label) ->
                            FilterChip(row.direction==value,onClick={replace(index,row.copy(direction=value))},enabled=enabled,
                                label={Text(label)},modifier=Modifier.testTag("series_reminder_direction_${index}_$value"))
                        }
                    }
                    if(row.direction!="ON") OutlinedTextField(row.dayCount,{replace(index,row.copy(dayCount=it))},label={Text("相差天數")},
                        enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                        modifier=Modifier.fillMaxWidth().testTag("series_reminder_days_$index"))
                    var picker by rememberSaveable { mutableStateOf(false) }
                    OutlinedTextField(row.time,{replace(index,row.copy(time=it))},label={Text("提醒時間（選填）")},placeholder={Text("HH:mm")},
                        enabled=enabled,singleLine=true,trailingIcon={TextButton(onClick={picker=true},enabled=enabled){Text("選擇")}},
                        modifier=Modifier.fillMaxWidth().testTag("series_reminder_time_$index"))
                    if(picker) TodoTimePicker(runCatching{parseTimeInput(row.time)}.getOrNull() ?: time ?: runCatching{parseTimeInput(row.base)}.getOrNull() ?: DEFAULT_REMINDER_TIME,
                        onDismiss={picker=false},onTime={replace(index,row.copy(time=it.toString()));picker=false})
                    Text("留空採各次截止時間，否則使用下方提醒基準。已產生的固定時刻不因單純改截止而移動。",style=MaterialTheme.typography.bodySmall)
                }
                if(time==null) OutlinedTextField(row.base,{replace(index,row.copy(base=it))},label={Text("只有日期時的提醒基準")},placeholder={Text("HH:mm")},
                    enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("series_reminder_base_$index"))
                val preview=runCatching { val day=requireNotNull(date){"請先設定本次日期"};val z=requireNotNull(zone){"請先設定截止時區"}
                    resolveReminder(occurrenceReminder(row.spec(),"preview",day,time,z),day,time,z) }
                Text(preview.fold({"本次預覽：${reminderInstantLabel(it)}"},{it.message ?: "請填完整提醒"}),style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.testTag("series_reminder_preview_$index"))
            }
        }
    } }
    if(rows.size<32) OutlinedButton(onClick={onChange(encodeSeriesReminderInputs(rows+SeriesReminderInput()))},enabled=enabled,
        modifier=Modifier.fillMaxWidth().testTag("add_series_reminder")){Text("＋ 新增系列提醒")}
}
