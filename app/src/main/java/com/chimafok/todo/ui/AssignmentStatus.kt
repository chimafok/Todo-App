package com.chimafok.todo.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chimafok.todo.R
import com.chimafok.todo.domain.TaskStatus

@Composable
fun assignmentStatusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    TaskStatus.DONE_UNSUBMITTED -> Color(0xFFFFCF86)
    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Shape and accessible text identify states independently of their color. */
@Composable
fun AssignmentStatusIcon(status: TaskStatus, modifier: Modifier = Modifier) {
    val icon = when (status) {
        TaskStatus.IN_PROGRESS -> R.drawable.ic_status_progress
        TaskStatus.DONE_UNSUBMITTED -> R.drawable.ic_status_unsubmitted
        TaskStatus.COMPLETED -> R.drawable.ic_status_completed
        else -> R.drawable.ic_status_not_started
    }
    Icon(painterResource(icon), contentDescription = null,
        modifier = modifier.size(24.dp), tint = assignmentStatusColor(status))
}
