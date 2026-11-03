package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chimafok.todo.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DatePickerButton(initial: LocalDate, @StringRes description: Int, enabled: Boolean = true, onDate: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    IconButton(onClick = { keyboard?.hide(); open = true }, enabled = enabled) {
        Icon(painterResource(R.drawable.ic_agenda), stringResource(description))
    }
    if (open) TodoDatePicker(initial, onDismiss = { open = false }) {
        onDate(it)
        open = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoDatePicker(initial: LocalDate, onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
    val initialMillis = initial.withYear(initial.year.coerceIn(1, 9999)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis, yearRange = 1..9999)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                // DatePicker's midnight is UTC, not a timestamp in the device's zone.
                state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }, modifier = Modifier.testTag("confirm_date")) { Text(tr(R.string.text_ok_20db9f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_date")) { Text(tr(R.string.text_cancel_2cd0f3)) } },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("date_picker")) {
            DatePicker(
                state = state,
                showModeToggle = true,
                title = { Text(tr(R.string.text_choose_date_55e2f6), Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp), style = MaterialTheme.typography.labelLarge) },
                headline = {
                    val selected = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    Text(selected?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", com.chimafok.todo.i18n.I18n.locale)) ?: tr(R.string.text_no_date_selected_a7010e),
                        Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
                },
                colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoTimePicker(initial: LocalTime, onDismiss: () -> Unit, onTime: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var dial by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(R.string.text_choose_time_9f8b0c), style = MaterialTheme.typography.titleLarge)
            Text(tr(R.string.text_hour_time_b5a060), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("time_picker"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !dial, onClick = { dial = false }, label = { Text(tr(R.string.text_enter_time_341b45)) })
                    FilterChip(selected = dial, onClick = { dial = true }, label = { Text(tr(R.string.text_clock_119cdf)) })
                }
                if (dial) TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical) else TimeInput(state = state)
            }
        },
        confirmButton = { TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)) },
            modifier = Modifier.testTag("confirm_time")) { Text(tr(R.string.text_ok_20db9f)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_time")) { Text(tr(R.string.text_cancel_2cd0f3)) } },
    )
}
