package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
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
    RecurrenceFrequency.DAILY -> tr(R.string.text_daily_fd7b67)
    RecurrenceFrequency.WEEKLY -> tr(R.string.text_weekly_a8a3df)
    RecurrenceFrequency.WEEKDAYS -> tr(R.string.text_selected_weekdays_990286)
    RecurrenceFrequency.MONTHLY -> tr(R.string.text_monthly_68b21a)
    RecurrenceFrequency.EVERY_N_DAYS -> tr(R.string.text_every_n_days_9e9e21)
}

@Composable
fun RecurrenceFields(frequency: String, interval: String, weekdays: List<Int>, endMode: String, endDate: String, count: String,
    allowNone: Boolean, enabled: Boolean, onFrequency: (String) -> Unit, onInterval: (String) -> Unit,
    onWeekdays: (List<Int>) -> Unit, onEndMode: (String) -> Unit, onEndDate: (String) -> Unit, onCount: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowNone) FilterChip(frequency == "NONE", onClick = { onFrequency("NONE") }, enabled = enabled,
                label = { Text(tr(R.string.text_does_not_repeat_adcade)) }, modifier = Modifier.testTag("repeat_NONE"))
            RecurrenceFrequency.entries.forEach { choice ->
                FilterChip(frequency == choice.name, onClick = { onFrequency(choice.name) }, enabled = enabled,
                    label = { Text(recurrenceLabel(choice)) }, modifier = Modifier.testTag("repeat_${choice.name}"))
            }
        }
        if (frequency == RecurrenceFrequency.EVERY_N_DAYS.name) OutlinedTextField(interval, onInterval,
            label = { Text(tr(R.string.text_interval_in_days_8afe89)) }, singleLine = true, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("repeat_interval"), shape = MaterialTheme.shapes.medium)
        if (frequency == RecurrenceFrequency.WEEKDAYS.name) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to tr(R.string.text_mon_51a75f), 2 to tr(R.string.text_tue_084b42), 3 to tr(R.string.text_wed_a4c331), 4 to tr(R.string.text_thu_754a9d), 5 to tr(R.string.text_fri_c9b87f), 6 to tr(R.string.text_sat_de07b5), 7 to tr(R.string.text_sun_85217f)).forEach { (day, text) ->
                FilterChip(day in weekdays, onClick = { onWeekdays(if (day in weekdays) weekdays - day else weekdays + day) },
                    enabled = enabled, label = { Text(text) }, modifier = Modifier.testTag("repeat_weekday_$day"))
            }
        }
        if (frequency != "NONE") {
            Text(tr(R.string.text_ends_2d2efc), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NONE" to tr(R.string.text_never_c329ac), "DATE" to tr(R.string.text_end_date_07dd3b), "COUNT" to tr(R.string.text_total_occurrences_7c221a)).forEach { (mode, label) ->
                    FilterChip(endMode == mode, onClick = { onEndMode(mode) }, enabled = enabled,
                        label = { Text(label) }, modifier = Modifier.testTag("repeat_end_$mode"))
                }
            }
            if (endMode == "DATE") OutlinedTextField(endDate, onEndDate, label = { Text(tr(R.string.text_end_date_inclusive_1ac76f)) }, placeholder = { Text("YYYY-MM-DD") },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("repeat_end_date"), shape = MaterialTheme.shapes.medium)
            if (endMode == "COUNT") OutlinedTextField(count, onCount, label = { Text(tr(R.string.text_total_occurrences_including_the_first_and_fe3b61)) },
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("repeat_count"), shape = MaterialTheme.shapes.medium)
        }
    }
}
