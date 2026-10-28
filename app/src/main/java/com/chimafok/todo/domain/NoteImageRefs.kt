package com.chimafok.todo.domain

/** Ordered, unique content hashes. No per-note image count limit. */
fun decodeImageIds(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    require((value.length.toLong() + 1) % 65 == 0L) { "圖片附件識別碼無效" }
    val ids = linkedSetOf<String>()
    var offset = 0
    while (offset < value.length) {
        for (n in offset until offset + 64) require(value[n] in 'a'..'f' || value[n] in '0'..'9') { "圖片附件識別碼無效" }
        require(ids.add(value.substring(offset, offset + 64))) { "圖片附件識別碼重複" }
        if (offset + 64 < value.length) require(value[offset + 64] == ',') { "圖片附件識別碼無效" }
        offset += 65
    }
    return ids.toList()
}
fun encodeImageIds(ids: List<String>): String = ids.joinToString(",").also { decodeImageIds(it) }
