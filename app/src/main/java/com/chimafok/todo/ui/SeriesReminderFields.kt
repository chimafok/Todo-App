package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
        val baseline = requireNotNull(parseTimeInput(base)) { tr(R.string.text_enter_the_reminder_baseline_for_date_332097) }
        return if (type == SeriesReminderKind.RELATIVE_DUE) SeriesReminderSpec(id, type,
            offsetMinutes = reminderOffsetMinutes(quantity, ReminderOffsetUnit.valueOf(unit)), baseTime = baseline)
        else {
            require(direction in listOf("BEFORE", "ON", "AFTER")) { tr(R.string.text_invalid_date_direction_2c4d4a) }
            val days = if (direction == "ON") 0 else {
                require(dayCount.trim().matches(Regex("[0-9]+"))) { tr(R.string.text_enter_a_valid_number_of_days_883d5b) }
                requireNotNull(dayCount.trim().toIntOrNull()) { tr(R.string.text_days_out_of_range_46e8dc) }.also { require(it in 0..3650) { tr(R.string.text_date_offset_cannot_exceed_days_dfc367) } }
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
    Text(tr(R.string.text_series_reminders_51fbcb), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(tr(R.string.text_applies_to_every_occurrence_each_time_644875), style = MaterialTheme.typography.bodySmall)
    if (rows.isNotEmpty()) ReminderCapabilities()
    rows.forEachIndexed { index, row -> key(row.id) {
        OutlinedCard(Modifier.fillMaxWidth().testTag("series_reminder_row_$index")) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tr(R.string.text_reminder_s_d115cc, index + 1), style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = enabled, onClick = { onChange(encodeSeriesReminderInputs(rows.filterNot { it.id == row.id })) },
                        modifier = Modifier.testTag("remove_series_reminder_$index")) { Text(tr(R.string.text_remove_6135d4)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(row.kind == "RELATIVE_DUE", enabled = enabled, onClick = { replace(index,row.copy(kind="RELATIVE_DUE")) },
                        label = { Text(tr(R.string.text_before_due_ffbb05)) }, modifier = Modifier.testTag("series_reminder_relative_$index"))
                    FilterChip(row.kind == "OCCURRENCE_TIME", enabled = enabled, onClick = { replace(index,row.copy(kind="OCCURRENCE_TIME")) },
                        label = { Text(tr(R.string.text_time_for_each_occurrence_bd2b4a)) }, modifier = Modifier.testTag("series_reminder_fixed_$index"))
                }
                if (row.kind == "RELATIVE_DUE") {
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(row.quantity, { replace(index,row.copy(quantity=it)) }, label = { Text(tr(R.string.text_amount_before_due_007cfb)) }, enabled=enabled,
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
                    Text(tr(R.string.text_each_occurrence_date_2bf2ea),style=MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("BEFORE" to tr(R.string.text_before_76e519), "ON" to tr(R.string.text_on_the_day_0c470f), "AFTER" to tr(R.string.text_after_ade39a)).forEach { (value,label) ->
                            FilterChip(row.direction==value,onClick={replace(index,row.copy(direction=value))},enabled=enabled,
                                label={Text(label)},modifier=Modifier.testTag("series_reminder_direction_${index}_$value"))
                        }
                    }
                    if(row.direction!="ON") OutlinedTextField(row.dayCount,{replace(index,row.copy(dayCount=it))},label={Text(tr(R.string.text_days_apart_f21324))},
                        enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                        modifier=Modifier.fillMaxWidth().testTag("series_reminder_days_$index"))
                    var picker by rememberSaveable { mutableStateOf(false) }
                    OutlinedTextField(row.time,{replace(index,row.copy(time=it))},label={Text(tr(R.string.text_reminder_time_optional_ae1363))},placeholder={Text("HH:mm")},
                        enabled=enabled,singleLine=true,trailingIcon={TextButton(onClick={picker=true},enabled=enabled){Text(tr(R.string.text_choose_a50371))}},
                        modifier=Modifier.fillMaxWidth().testTag("series_reminder_time_$index"))
                    if(picker) TodoTimePicker(runCatching{parseTimeInput(row.time)}.getOrNull() ?: time ?: runCatching{parseTimeInput(row.base)}.getOrNull() ?: DEFAULT_REMINDER_TIME,
                        onDismiss={picker=false},onTime={replace(index,row.copy(time=it.toString()));picker=false})
                    Text(tr(R.string.text_leave_blank_to_use_each_due_95b3e1),style=MaterialTheme.typography.bodySmall)
                }
                if(time==null) OutlinedTextField(row.base,{replace(index,row.copy(base=it))},label={Text(tr(R.string.text_the_date_only_reminder_baseline_8bf5b1))},placeholder={Text("HH:mm")},
                    enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("series_reminder_base_$index"))
                val preview=runCatching { val day=requireNotNull(date){tr(R.string.text_set_this_occurrence_date_first_22df94)};val z=requireNotNull(zone){tr(R.string.text_set_the_due_time_zone_first_ad95f4)}
                    resolveReminder(occurrenceReminder(row.spec(),"preview",day,time,z),day,time,z) }
                Text(preview.fold({tr(R.string.text_this_occurrence_preview_s_2ad43b, reminderInstantLabel(it))},{it.message ?: tr(R.string.text_complete_the_reminder_details_0f56b9)}),style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.testTag("series_reminder_preview_$index"))
            }
        }
    } }
    if(rows.size<32) OutlinedButton(onClick={onChange(encodeSeriesReminderInputs(rows+SeriesReminderInput()))},enabled=enabled,
        modifier=Modifier.fillMaxWidth().testTag("add_series_reminder")){Text(tr(R.string.text_add_series_reminder_631ec5))}
}
