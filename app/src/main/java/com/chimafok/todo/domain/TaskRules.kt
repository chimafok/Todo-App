package com.chimafok.todo.domain

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
)

const val UNCATEGORIZED_ID = "00000000-0000-4000-8000-000000000001"
const val ASSIGNMENTS_ID = "00000000-0000-4000-8000-000000000002"
const val STUDY_ID = "00000000-0000-4000-8000-000000000003"

fun validatedTitle(title: String): String {
    val result = title.trim()
    require(result.isNotEmpty() && result.length <= 200) { "名稱須為 1–200 個字元" }
    return result
}

fun validateDraft(draft: TaskDraft, assignment: Boolean) {
    validatedTitle(draft.title)
    require(draft.notes.length <= 20_000) { "備註不可超過 20,000 個字元" }
    require(draft.dueTime == null || draft.dueDate != null) { "設定時間前請先選擇日期" }
    require(draft.dueTime == null || draft.zoneId != null) { "截止時間缺少時區" }
    draft.zoneId?.let(ZoneId::of)
    require(draft.startDate == null || draft.startDate.year in 1..9999) { "開始日期超出範圍" }
    require(draft.startTime == null || draft.startDate != null) { "設定開始時間前請先選擇開始日期" }
    require(draft.startTime == null || draft.startZoneId != null) { "開始時間缺少時區" }
    require(draft.startDate != null || draft.startZoneId == null) { "開始時區缺少日期" }
    draft.startZoneId?.let(ZoneId::of)
    if (draft.startDate != null && draft.startTime != null) deadlineInstant(draft.startDate, draft.startTime, requireNotNull(draft.startZoneId))
    val allowed = if (assignment) TaskStatus.entries.filter { it != TaskStatus.OPEN }
        else listOf(TaskStatus.OPEN, TaskStatus.COMPLETED)
    require(draft.status in allowed) { "任務狀態與分類不相符" }
}

fun isOverdue(date: LocalDate?, time: LocalTime?, zoneId: String?, clock: Clock): Boolean {
    if (date == null) return false
    if (time == null) return date < LocalDate.now(clock)
    return date.atTime(time).atZone(ZoneId.of(requireNotNull(zoneId))).toInstant() < clock.instant()
}

fun convertStatus(status: TaskStatus, assignment: Boolean, selected: TaskStatus? = null): TaskStatus =
    if (assignment) {
        requireNotNull(selected) { "請選擇作業狀態" }.also {
            require(it != TaskStatus.OPEN) { "請選擇作業狀態" }
        }
    } else if (status == TaskStatus.COMPLETED) TaskStatus.COMPLETED else TaskStatus.OPEN

fun nextAssignmentStatus(status: TaskStatus): TaskStatus = when (status) {
    TaskStatus.NOT_STARTED -> TaskStatus.IN_PROGRESS
    TaskStatus.IN_PROGRESS -> TaskStatus.DONE_UNSUBMITTED
    TaskStatus.DONE_UNSUBMITTED -> TaskStatus.COMPLETED
    else -> error("此狀態無法快速推進")
}
