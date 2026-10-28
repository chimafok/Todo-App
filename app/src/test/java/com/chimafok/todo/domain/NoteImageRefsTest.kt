package com.chimafok.todo.domain
import org.junit.Assert.*
import org.junit.Test
class NoteImageRefsTest {
    @Test fun emptyAndMultipleIdsRoundTripInOrder() { val ids = listOf("a".repeat(64), "b".repeat(64)); assertEquals(ids, decodeImageIds(encodeImageIds(ids))); assertEquals(emptyList<String>(), decodeImageIds("")) }
    @Test fun manyImagesRoundTripWithoutCountLimit() {
        val ids = (0 until 500).map { it.toString(16).padStart(64, '0') }
        assertEquals(ids, decodeImageIds(encodeImageIds(ids)))
    }
    @Test fun rejectsInvalidIdsAndDuplicates() {
        listOf("a".repeat(1_000_000), "../file", "a".repeat(64)+","+"a".repeat(64),
            "g".repeat(64), "a".repeat(64)+";"+"b".repeat(64)).forEach { value ->
            var no = false; try { decodeImageIds(value) } catch (_: IllegalArgumentException) { no = true }; assertTrue(value.take(80), no)
        }
    }
}
