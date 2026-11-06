package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
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
    var hourValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial.hour.toString().padStart(2, '0')))
    }
    var minuteValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial.minute.toString().padStart(2, '0')))
    }
    val inputValid = hourValue.text.toIntOrNull() in 0..23 && minuteValue.text.toIntOrNull() in 0..59

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
                    FilterChip(selected = !dial, onClick = {
                        hourValue = TextFieldValue(state.hour.toString().padStart(2, '0'))
                        minuteValue = TextFieldValue(state.minute.toString().padStart(2, '0'))
                        dial = false
                    }, modifier = Modifier.testTag("time_input_mode"), label = { Text(tr(R.string.text_enter_time_341b45)) })
                    FilterChip(selected = dial, onClick = { dial = true }, modifier = Modifier.testTag("time_dial_mode"), label = { Text(tr(R.string.text_clock_119cdf)) })
                }
                if (dial) TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical) else TodoTimeInput(
                    hourValue, minuteValue,
                    onHourChange = { state.selection = TimePickerSelectionMode.Hour; hourValue = it; it.text.toIntOrNull()?.let { hour -> state.hour = hour } },
                    onMinuteChange = { state.selection = TimePickerSelectionMode.Minute; minuteValue = it; it.text.toIntOrNull()?.let { minute -> state.minute = minute } },
                )
            }
        },
        confirmButton = { TextButton(enabled = dial || inputValid, onClick = { onTime(LocalTime.of(state.hour, state.minute)) },
            modifier = Modifier.testTag("confirm_time")) { Text(tr(R.string.text_ok_20db9f)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_time")) { Text(tr(R.string.text_cancel_2cd0f3)) } },
    )
}

/** Keep raw digits so leading zeroes and an unchanged initial hour also advance correctly. */
@Composable
private fun TodoTimeInput(
    hour: TextFieldValue,
    minute: TextFieldValue,
    onHourChange: (TextFieldValue) -> Unit,
    onMinuteChange: (TextFieldValue) -> Unit,
) {
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var advanceToMinute by remember { mutableStateOf(false) }
    fun accepts(value: TextFieldValue, max: Int) = value.text.length <= 2 &&
        value.text.all { it in '0'..'9' } && (value.text.isEmpty() || value.text.toIntOrNull() in 0..max)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = 48.sp, lineHeight = 56.sp, textAlign = TextAlign.Center,
        fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    // Reserve space for padding and the caret, including OEM fonts and larger font scales.
    val fit = remember(measurer, baseStyle, density.density, density.fontScale) {
        val samples = (0..9).map { measurer.measure(it.toString().repeat(2), baseStyle) }
        minOf(1f, with(density) { 72.dp.toPx() } / samples.maxOf { it.size.width },
            with(density) { 80.dp.toPx() } / samples.maxOf { it.size.height })
    }
    val style = baseStyle.copy(fontSize = baseStyle.fontSize * fit, lineHeight = baseStyle.lineHeight * fit)
    val hourLabel = tr(R.string.text_hours_507ac2)
    val minuteLabel = tr(R.string.text_minutes_c26fdd)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimeNumberField(
                    value = hour, label = hourLabel, style = style,
                    onValueChange = { next ->
                        if (accepts(next, 23)) {
                            onHourChange(next)
                            // Selection-only changes, deleting and a single digit keep Hour focused.
                            if (next.text != hour.text && next.text.length == 2) advanceToMinute = true
                        }
                    },
                    modifier = Modifier.width(96.dp).height(96.dp).testTag("time_hour_input")
                        .focusRequester(hourFocus).onFocusChanged {
                            if (it.isFocused) onHourChange(hour.copy(selection = TextRange(0, hour.text.length)))
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { minuteFocus.requestFocus() }),
                )
                Text(hourLabel, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.width(32.dp).height(96.dp), contentAlignment = Alignment.Center) {
                Text(":", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimeNumberField(
                    value = minute, label = minuteLabel, style = style,
                    onValueChange = { if (accepts(it, 59)) onMinuteChange(it) },
                    modifier = Modifier.width(96.dp).height(96.dp).testTag("time_minute_input")
                        .focusRequester(minuteFocus).onFocusChanged {
                            if (it.isFocused) onMinuteChange(minute.copy(selection = TextRange(0, minute.text.length)))
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                )
                Text(minuteLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    LaunchedEffect(Unit) { hourFocus.requestFocus() }
    LaunchedEffect(advanceToMinute) {
        if (advanceToMinute) {
            // Apply the new text before changing focus so the IME cannot restore stale text.
            minuteFocus.requestFocus()
            advanceToMinute = false
        }
    }
}

@Composable
private fun TimeNumberField(
    value: TextFieldValue,
    label: String,
    style: TextStyle,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val container = if (focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = label }.onFocusChanged { focused = it.isFocused },
        singleLine = true, textStyle = style.copy(color = foreground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        decorationBox = { editor ->
            Box(Modifier.fillMaxSize().background(container, shape)
                .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                .padding(8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center, propagateMinConstraints = true) { editor() }
            }
        },
    )
}
