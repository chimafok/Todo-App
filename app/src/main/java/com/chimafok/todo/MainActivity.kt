package com.chimafok.todo

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chimafok.todo.ui.TodoApp
import com.chimafok.todo.ui.TodoViewModel
import com.chimafok.todo.ui.theme.TodoTheme

class MainActivity : ComponentActivity() {
    private val todoViewModel: TodoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean("externalDocumentFlow") == true) {
            todoViewModel.externalDocumentFlow = true
            todoViewModel.destination = com.chimafok.todo.ui.Destination.SETTINGS
        }
        todoViewModel.externalImageFlow = savedInstanceState?.getBoolean("externalImageFlow") == true
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) todoViewModel.openReminder(intent.getStringExtra("reminderTaskId"), intent.getLongExtra("reminderTaskRevision", -1))
        setContent {
            com.chimafok.todo.i18n.LocalizedContent {
                TodoTheme { TodoApp(todoViewModel) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.chimafok.todo.i18n.I18n.refreshSystem()
        todoViewModel.onForeground()
    }

    override fun onRestart() {
        super.onRestart()
        if (!todoViewModel.externalDocumentFlow && !todoViewModel.externalImageFlow) todoViewModel.goHome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("externalImageFlow", todoViewModel.externalImageFlow)
        outState.putBoolean("externalDocumentFlow", todoViewModel.externalDocumentFlow)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        todoViewModel.goHome()
        todoViewModel.openReminder(intent.getStringExtra("reminderTaskId"), intent.getLongExtra("reminderTaskRevision", -1))
    }
}
