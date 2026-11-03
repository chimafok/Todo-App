package com.chimafok.todo.domain

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
/** Ordered, unique content hashes. No per-note image count limit. */
fun decodeImageIds(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    require((value.length.toLong() + 1) % 65 == 0L) { tr(R.string.text_invalid_image_attachment_id_3f91c4) }
    val ids = linkedSetOf<String>()
    var offset = 0
    while (offset < value.length) {
        for (n in offset until offset + 64) require(value[n] in 'a'..'f' || value[n] in '0'..'9') { tr(R.string.text_invalid_image_attachment_id_3f91c4) }
        require(ids.add(value.substring(offset, offset + 64))) { tr(R.string.text_duplicate_image_attachment_id_ef6420) }
        if (offset + 64 < value.length) require(value[offset + 64] == ',') { tr(R.string.text_invalid_image_attachment_id_3f91c4) }
        offset += 65
    }
    return ids.toList()
}
fun encodeImageIds(ids: List<String>): String = ids.joinToString(",").also { decodeImageIds(it) }
