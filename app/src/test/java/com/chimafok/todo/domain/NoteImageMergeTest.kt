package com.chimafok.todo.domain
import com.chimafok.todo.data.*
import org.junit.Assert.*
import org.junit.Test

class NoteImageMergeTest {
    private val a = "a".repeat(64); private val b = "b".repeat(64)
    private fun graph(image: String) = BackupGraph(mapOf("tasks" to listOf(mapOf("id" to "task", "title" to "task", "notes" to "", "categoryId" to UNCATEGORIZED_ID,
        "status" to "OPEN", "createdAt" to 1L, "updatedAt" to 2L, "imageIds" to image)), "note_images" to listOf(mapOf("id" to image, "data" to "fixture"))))
    @Test fun conflictingTaskChoiceCarriesOnlyItsImages() {
        val local = graph(a); val file = graph(b); val preview = planBackup(local, file, RestoreMode.MERGE)
        assertEquals(1, preview.conflicts.size)
        val chosen = planBackup(local, file, RestoreMode.MERGE, mapOf(preview.conflicts.single().id to BackupChoice.FILE))
        assertEquals(listOf(b), chosen.graph.rows("note_images").map { it.text("id") })
        assertEquals(listOf(a), preview.graph.rows("note_images").map { it.text("id") })
    }
    @Test fun replacementDropsUnusedLocalImagesAndIdenticalMergeDeduplicates() {
        val same = planBackup(graph(a), graph(a), RestoreMode.MERGE); assertTrue(same.conflicts.isEmpty()); assertEquals(1, same.graph.rows("note_images").size)
        val replaced = planBackup(graph(a), BackupGraph(emptyMap()), RestoreMode.REPLACE); assertTrue(replaced.graph.rows("note_images").isEmpty())
    }
}
