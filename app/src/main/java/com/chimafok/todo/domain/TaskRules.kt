package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class TaskStatus { OPEN, NOT_STARTED, IN_PROGRESS, DONE_UNSUBMITTED, COMPLETED }
enum class SystemCategory { UNCATEGORIZED, ASSIGNMENTS }

data class TaskDraft(
    val id: String,
    val title: String,
    val notes: String = "",
    val categoryId: String = UNCATEGORIZED_ID,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val zoneId: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val expectedRevision: Long? = null,
    val tagIds: Set<String> = emptySet(),
    val recurrence: RecurrenceRule? = null,
    val expectedSeriesRevision: Long? = null,
    val expectedOccurrenceRevisions: Map<String, Long>? = null,
    val reminders: List<ReminderSpec>? = null,
    val seriesReminders: List<SeriesReminderSpec>? = null,
    val resetSeriesReminders: Boolean = false,
    val inheritSingleReminders: Boolean = false,
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val startZoneId: String? = null,
    val imageIds: String = "",
)

const val UNCATEGORIZED_ID = "00000000-0000-4000-8000-000000000001"
const val ASSIGNMENTS_ID = "00000000-0000-4000-8000-000000000002"
const val STUDY_ID = "00000000-0000-4000-8000-000000000003"

/** Protection follows identity so renaming Study never makes it deletable. */
fun canDeleteCategory(id: String, systemKind: SystemCategory?): Boolean = systemKind == null && id != STUDY_ID

fun validatedTitle(title: String): String {
    val result = title.trim()
    require(result.isNotEmpty() && result.length <= 200) { tr(R.string.text_task_names_must_contain_characters_8e4dfe) }
    return result
}

fun validateDraft(draft: TaskDraft, assignment: Boolean) {
    validatedTitle(draft.title)
    decodeImageIds(draft.imageIds)
    require(draft.notes.length <= 20_000) { tr(R.string.text_notes_cannot_exceed_characters_4ee22c) }
    require(draft.dueTime == null || draft.dueDate != null) { tr(R.string.text_choose_a_date_before_setting_a_6c327e) }
    require(draft.dueTime == null || draft.zoneId != null) { tr(R.string.text_the_due_time_requires_a_time_6c3eea) }
    draft.zoneId?.let(ZoneId::of)
    require(draft.startDate == null || draft.startDate.year in 1..9999) { tr(R.string.text_start_date_out_of_range_f5cecb) }
    require(draft.startTime == null || draft.startDate != null) { tr(R.string.text_choose_a_start_date_before_setting_144485) }
    require(draft.startTime == null || draft.startZoneId != null) { tr(R.string.text_the_start_time_requires_a_time_9b40e3) }
    require(draft.startDate != null || draft.startZoneId == null) { tr(R.string.text_the_start_time_zone_requires_a_f7f3b8) }
    draft.startZoneId?.let(ZoneId::of)
    if (draft.startDate != null && draft.startTime != null) deadlineInstant(draft.startDate, draft.startTime, requireNotNull(draft.startZoneId))
    val allowed = statusOptions(assignment, draft.categoryId == STUDY_ID)
    require(draft.status in allowed) { tr(R.string.text_task_status_does_not_match_its_7cb5a5) }
}

fun isOverdue(date: LocalDate?, time: LocalTime?, zoneId: String?, clock: Clock): Boolean {
    if (date == null) return false
    if (time == null) return date < LocalDate.now(clock)
    return date.atTime(time).atZone(ZoneId.of(requireNotNull(zoneId))).toInstant() < clock.instant()
}

// Learning reuses OPEN for 未開始 so existing tasks need no destructive rewrite.
fun statusOptions(assignment: Boolean, study: Boolean = false): List<TaskStatus> = when {
    assignment -> TaskStatus.entries.filter { it != TaskStatus.OPEN }
    study -> listOf(TaskStatus.OPEN, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED)
    else -> listOf(TaskStatus.OPEN, TaskStatus.COMPLETED)
}

fun convertStatus(status: TaskStatus, assignment: Boolean, selected: TaskStatus? = null, study: Boolean = false): TaskStatus =
    if (assignment) {
        requireNotNull(selected) { tr(R.string.text_choose_an_assignment_status_3fcfdf) }.also {
            require(it != TaskStatus.OPEN) { tr(R.string.text_choose_an_assignment_status_3fcfdf) }
        }
    } else if (study) when (status) {
        TaskStatus.COMPLETED -> TaskStatus.COMPLETED
        TaskStatus.IN_PROGRESS, TaskStatus.DONE_UNSUBMITTED -> TaskStatus.IN_PROGRESS
        else -> TaskStatus.OPEN
    } else if (status == TaskStatus.COMPLETED) TaskStatus.COMPLETED else TaskStatus.OPEN

fun nextAssignmentStatus(status: TaskStatus): TaskStatus = when (status) {
    TaskStatus.NOT_STARTED -> TaskStatus.IN_PROGRESS
    TaskStatus.IN_PROGRESS -> TaskStatus.DONE_UNSUBMITTED
    TaskStatus.DONE_UNSUBMITTED -> TaskStatus.COMPLETED
    else -> error(tr(R.string.text_this_status_cannot_advance_directly_ac4c25))
}

fun nextProgressStatus(status: TaskStatus, study: Boolean): TaskStatus = if (!study) nextAssignmentStatus(status) else when (status) {
    TaskStatus.OPEN -> TaskStatus.IN_PROGRESS
    TaskStatus.IN_PROGRESS -> TaskStatus.COMPLETED
    else -> error(tr(R.string.text_this_study_status_cannot_advance_directly_ba998e))
}
