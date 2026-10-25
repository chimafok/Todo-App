package com.chimafok.todo.domain

import com.chimafok.todo.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupMergeTest {
    private fun row(id:String,title:String="task",at:Long=100,deleted:Long?=null):BackupRow=mapOf("id" to id,"title" to title,"notes" to "", "categoryId" to UNCATEGORIZED_ID, "status" to "OPEN","updatedAt" to at,"createdAt" to 1L,"deletedAt" to deleted)
    private fun graph(vararg tasks:BackupRow)=BackupGraph(mapOf("tasks" to tasks.toList()))
    @Test fun sameNameDifferentIdsRemainIndependent(){val p=planBackup(graph(row("one")),graph(row("two")),RestoreMode.MERGE);assertEquals(2,p.graph.rows("tasks").size);assertTrue(p.conflicts.isEmpty());assertEquals(1,p.added)}
    @Test fun sameIdNewerChangesWinAndOldFilesCannotOverwrite(){val a=graph(row("one","local",200));val b=graph(row("one","old",100));assertEquals("local",planBackup(a,b,RestoreMode.MERGE).graph.rows("tasks").single()["title"]);assertEquals("local",planBackup(b,a,RestoreMode.MERGE).graph.rows("tasks").single()["title"])}
    @Test fun identicalContentSkipsEvenIfFileTimeUnknownOrDifferent(){val p=planBackup(graph(row("one",at=200)),graph(row("one",at=0)),RestoreMode.MERGE);assertTrue(p.conflicts.isEmpty());assertEquals(0,p.changed);assertEquals(200L,p.graph.rows("tasks").single()["updatedAt"])}
    @Test fun equalOrUnknownModificationRequiresAnExplicitVersion(){for(at in listOf(100L,0L)){val a=graph(row("one","local"));val b=graph(row("one","file",at));val p=planBackup(a,b,RestoreMode.MERGE);assertEquals(1,p.conflicts.size);val id=p.conflicts.single().id;assertEquals("file",planBackup(a,b,RestoreMode.MERGE,mapOf(id to BackupChoice.FILE)).graph.rows("tasks").single()["title"])}}
    @Test fun trashNeverResurrectsAutomaticallyEvenWithANewerFile(){val p=planBackup(graph(row("one",deleted=10)),graph(row("one","new",999)),RestoreMode.MERGE);assertEquals(1,p.conflicts.size);assertEquals(10L,p.graph.rows("tasks").single()["deletedAt"])}
    @Test fun replaceReportsRemovedTasksAndRetainsOnlyFileRows(){val p=planBackup(graph(row("one"),row("two")),graph(row("new")),RestoreMode.REPLACE);assertEquals(2,p.removed);assertEquals(1,p.added);assertEquals(listOf("new"),p.graph.rows("tasks").map { it["id"] })}
    @Test fun linksFollowTheNewerOrdinaryTaskWithoutMatchingNames(){val a=graph(row("one",at=100));val b=BackupGraph(mapOf("tasks" to listOf(row("one",at=200)),"task_tags" to listOf(mapOf("taskId" to "one","tagId" to "tag")),"tags" to listOf(mapOf("id" to "tag","name" to "same","createdAt" to 1L,"updatedAt" to 2L))));val p=planBackup(a,b,RestoreMode.MERGE);assertTrue(p.conflicts.isEmpty());assertEquals(1,p.graph.rows("task_tags").size);assertEquals(1,p.changed)}
    @Test fun conversionConnectsOldOrdinaryIdAndNewSeriesAsOneConflict(){val a=graph(row("one"));val b=BackupGraph(mapOf("tasks" to listOf(row("one")),"recurring_series" to listOf(mapOf("id" to "one","title" to "series","categoryId" to UNCATEGORIZED_ID,"cursor" to 1L,"updatedAt" to 200L)),"occurrences" to listOf(mapOf("taskId" to "one","seriesId" to "one","sequence" to 1L,"ruleId" to "rule"))));val p=planBackup(a,b,RestoreMode.MERGE);assertEquals(1,p.conflicts.size);val selected=planBackup(a,b,RestoreMode.MERGE,mapOf(p.conflicts.single().id to BackupChoice.FILE));assertEquals(1,selected.graph.rows("tasks").size);assertEquals(1,selected.graph.rows("occurrences").size)}
    @Test fun reusedReminderIdAcrossDifferentTasksCannotSilentlyDeleteALocalTask(){
        val a=BackupGraph(mapOf("tasks" to listOf(row("local")),"reminders" to listOf(mapOf("id" to "r","taskId" to "local","kind" to "FIXED","createdAt" to 1L,"updatedAt" to 100L))))
        val b=BackupGraph(mapOf("tasks" to listOf(row("file",at=200)),"reminders" to listOf(mapOf("id" to "r","taskId" to "file","kind" to "FIXED","createdAt" to 1L,"updatedAt" to 200L))))
        val p=planBackup(a,b,RestoreMode.MERGE);assertEquals(1,p.conflicts.size);assertEquals("local",p.graph.rows("tasks").single()["id"])
    }
    @Test fun contradictoryTaskAndReminderModificationTimesRequireAWholeTaskChoice(){
        val a=BackupGraph(mapOf("tasks" to listOf(row("one",at=200)),"reminders" to listOf(mapOf("id" to "r","taskId" to "one","fixedAt" to 1000L,"createdAt" to 1L,"updatedAt" to 50L))))
        val b=BackupGraph(mapOf("tasks" to listOf(row("one",at=100)),"reminders" to listOf(mapOf("id" to "r","taskId" to "one","fixedAt" to 2000L,"createdAt" to 1L,"updatedAt" to 300L))))
        assertEquals(1,planBackup(a,b,RestoreMode.MERGE).conflicts.size)
    }
    @Test fun canonicalEncodingEscapesStringsAndUsesOrderIndependentKeys(){
        val text=canonical(mapOf("b" to ("a"+10.toChar()+34.toChar()+92.toChar()+"漢😀"),"a" to 1L))
        assertTrue(text.startsWith("{"+34.toChar()+"a"+34.toChar()+":1,"));assertFalse(text.contains(10.toChar()))
        assertTrue(text.contains(92.toChar()+"u000a"))
        assertEquals(backupHash(mapOf("a" to 1L,"b" to 2L)),backupHash(mapOf("b" to 2L,"a" to 1L)))
    }
}
