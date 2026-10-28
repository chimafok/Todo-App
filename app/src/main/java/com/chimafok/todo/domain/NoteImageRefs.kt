package com.chimafok.todo.domain

const val MAX_NOTE_IMAGES = 8
private val imageIdPattern = Regex("[a-f0-9]{64}")
fun decodeImageIds(value: String): List<String> {
    require(value.length <= MAX_NOTE_IMAGES * 65 - 1) { "圖片附件識別碼過長" }
    return if (value.isEmpty()) emptyList() else value.split(',').also { ids ->
    require(ids.size <= MAX_NOTE_IMAGES && ids.distinct().size == ids.size && ids.all { imageIdPattern.matches(it) }) { "圖片附件識別碼無效" }
    }
}
fun encodeImageIds(ids: List<String>): String = ids.joinToString(",").also { decodeImageIds(it) }
