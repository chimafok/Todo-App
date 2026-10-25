package com.chimafok.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.RecurrenceFrequency

data class RecurringEditorContext(val series: RecurringSeriesEntity, val rule: SeriesRuleEntity, val occurrence: OccurrenceEntity,
    val records: List<com.chimafok.todo.domain.SeriesEditRecord> = emptyList(),
    val reminders: List<SeriesReminderEntity> = emptyList(), val reminderOverridden: Boolean = false)

fun recurrenceLabel(frequency: RecurrenceFrequency): String = when (frequency) {
    RecurrenceFrequency.DAILY -> "每日"
    RecurrenceFrequency.WEEKLY -> "每週"
    RecurrenceFrequency.WEEKDAYS -> "指定星期"
    RecurrenceFrequency.MONTHLY -> "每月"
    RecurrenceFrequency.EVERY_N_DAYS -> "每隔 N 天"
}

@Composable
fun RecurrenceFields(frequency: String, interval: String, weekdays: List<Int>, endMode: String, endDate: String, count: String,
    allowNone: Boolean, enabled: Boolean, onFrequency: (String) -> Unit, onInterval: (String) -> Unit,
    onWeekdays: (List<Int>) -> Unit, onEndMode: (String) -> Unit, onEndDate: (String) -> Unit, onCount: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowNone) FilterChip(frequency == "NONE", onClick = { onFrequency("NONE") }, enabled = enabled,
                label = { Text("不重複") }, modifier = Modifier.testTag("repeat_NONE"))
            RecurrenceFrequency.entries.forEach { choice ->
                FilterChip(frequency == choice.name, onClick = { onFrequency(choice.name) }, enabled = enabled,
                    label = { Text(recurrenceLabel(choice)) }, modifier = Modifier.testTag("repeat_${choice.name}"))
            }
        }
        if (frequency == RecurrenceFrequency.EVERY_N_DAYS.name) OutlinedTextField(interval, onInterval,
            label = { Text("間隔天數（1–3650）") }, singleLine = true, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("repeat_interval"), shape = MaterialTheme.shapes.medium)
        if (frequency == RecurrenceFrequency.WEEKDAYS.name) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (day, text) ->
                FilterChip(day in weekdays, onClick = { onWeekdays(if (day in weekdays) weekdays - day else weekdays + day) },
                    enabled = enabled, label = { Text(text) }, modifier = Modifier.testTag("repeat_weekday_$day"))
            }
        }
        if (frequency != "NONE") {
            Text("結束條件", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NONE" to "一直重複", "DATE" to "結束日期", "COUNT" to "總次數").forEach { (mode, label) ->
                    FilterChip(endMode == mode, onClick = { onEndMode(mode) }, enabled = enabled,
                        label = { Text(label) }, modifier = Modifier.testTag("repeat_end_$mode"))
                }
            }
            if (endMode == "DATE") OutlinedTextField(endDate, onEndDate, label = { Text("結束日期（含當天）") }, placeholder = { Text("YYYY-MM-DD") },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("repeat_end_date"), shape = MaterialTheme.shapes.medium)
            if (endMode == "COUNT") OutlinedTextField(count, onCount, label = { Text("總次數（含第一筆與已產生紀錄）") },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("repeat_count"), shape = MaterialTheme.shapes.medium)
        }
    }
}
