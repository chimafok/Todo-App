package com.chimafok.todo.domain
import org.junit.Assert.*
import org.junit.Test
class NoteImageRefsTest {
    @Test fun emptyAndMultipleIdsRoundTripInOrder() { val ids = listOf("a".repeat(64), "b".repeat(64)); assertEquals(ids, decodeImageIds(encodeImageIds(ids))); assertEquals(emptyList<String>(), decodeImageIds("")) }
    @Test fun rejectsInvalidIdsDuplicatesAndMoreThanEight() { listOf("a".repeat(1_000_000), "../file", "a".repeat(64)+","+"a".repeat(64), (0..8).joinToString(",") { it.toString().padStart(64,'0') }).forEach { value -> var no = false; try { decodeImageIds(value) } catch (_: IllegalArgumentException) { no = true }; assertTrue(no) } }
}
