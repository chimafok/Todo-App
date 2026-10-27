package com.chimafok.todo.domain

import org.junit.Assert.*
import org.junit.Test

class StudyStatusRulesTest {
    @Test fun learningAllowsThreeStatesAndRejectsAssignmentOnlyStates() {
        assertEquals(listOf(TaskStatus.OPEN, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED), statusOptions(false, true))
        statusOptions(false,true).forEach { validateDraft(TaskDraft("study","學習",categoryId=STUDY_ID,status=it),false) }
        listOf(TaskStatus.NOT_STARTED,TaskStatus.DONE_UNSUBMITTED).forEach { status ->
            assertThrows(IllegalArgumentException::class.java) { validateDraft(TaskDraft("study","學習",categoryId=STUDY_ID,status=status),false) }
        }
    }
    @Test fun ordinaryAndAssignmentStateContractsStaySeparate() {
        assertEquals(listOf(TaskStatus.OPEN,TaskStatus.COMPLETED),statusOptions(false))
        assertEquals(listOf(TaskStatus.NOT_STARTED,TaskStatus.IN_PROGRESS,TaskStatus.DONE_UNSUBMITTED,TaskStatus.COMPLETED),statusOptions(true))
        assertThrows(IllegalArgumentException::class.java) { validateDraft(TaskDraft("ordinary","一般",status=TaskStatus.IN_PROGRESS),false) }
    }
    @Test fun categoryConversionsPreserveMeaningfulProgressAndCompletion() {
        assertEquals(TaskStatus.OPEN,convertStatus(TaskStatus.NOT_STARTED,false,study=true))
        assertEquals(TaskStatus.IN_PROGRESS,convertStatus(TaskStatus.IN_PROGRESS,false,study=true))
        assertEquals(TaskStatus.IN_PROGRESS,convertStatus(TaskStatus.DONE_UNSUBMITTED,false,study=true))
        assertEquals(TaskStatus.COMPLETED,convertStatus(TaskStatus.COMPLETED,false,study=true))
        assertEquals(TaskStatus.OPEN,convertStatus(TaskStatus.IN_PROGRESS,false))
        assertEquals(TaskStatus.COMPLETED,convertStatus(TaskStatus.COMPLETED,false))
    }
    @Test fun learningAdvancesDirectlyFromProgressToCompletion() {
        assertEquals(TaskStatus.IN_PROGRESS,nextProgressStatus(TaskStatus.OPEN,true))
        assertEquals(TaskStatus.COMPLETED,nextProgressStatus(TaskStatus.IN_PROGRESS,true))
        assertThrows(IllegalStateException::class.java) { nextProgressStatus(TaskStatus.COMPLETED,true) }
        assertEquals(TaskStatus.DONE_UNSUBMITTED,nextProgressStatus(TaskStatus.IN_PROGRESS,false))
    }
}
