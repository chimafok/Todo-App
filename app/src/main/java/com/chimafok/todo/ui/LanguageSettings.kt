package com.chimafok.todo.ui

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chimafok.todo.i18n.*
import kotlinx.coroutines.launch

@Composable fun LanguageSettings(viewModel: TodoViewModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.text_app_language_a05c0c), style = MaterialTheme.typography.titleMedium)
        Text(tr(R.string.text_changes_interface_text_only_tasks_notes_901bd7), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChoice.entries.forEach { option ->
                FilterChip(selected = I18n.choice == option, enabled = !busy,
                    modifier = Modifier.testTag("language_${option.name}"),
                    label = { Text(when(option) {
                        LanguageChoice.SYSTEM -> tr(R.string.text_system_default_462fb4)
                        LanguageChoice.TRADITIONAL -> "繁體中文"
                        LanguageChoice.SIMPLIFIED -> "简体中文"
                        LanguageChoice.ENGLISH -> "English"
                    }) }, onClick = {
                        busy = true; scope.launch {
                            try { viewModel.setLanguage(option); error = null }
                            catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e; error = tr(R.string.text_cannot_save_the_language_setting_please_12260e) }
                            finally { busy = false }
                        }
                    })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
