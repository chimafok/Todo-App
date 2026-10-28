package com.chimafok.todo.data

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import androidx.room.*
import com.chimafok.todo.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

@Entity(tableName = "note_images")
data class NoteImageEntity(@PrimaryKey val id: String, val data: String)

@Dao
interface NoteImageDao {
    @Query("SELECT * FROM note_images WHERE id = :id") suspend fun image(id: String): NoteImageEntity?
    @Query("SELECT id FROM note_images") suspend fun ids(): List<String>
    @Query("SELECT LENGTH(data) FROM note_images WHERE id = :id") suspend fun size(id: String): Int?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: NoteImageEntity)
    @Query("DELETE FROM note_images WHERE id = :id") suspend fun delete(id: String)
}

const val NOTE_IMAGE_MAX_BYTES = 768 * 1024
const val NOTE_IMAGE_MAX_EDGE = 1536
fun imageHash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
fun validateImageData(id: String, data: String): ByteArray {
    require(data.length <= (NOTE_IMAGE_MAX_BYTES + 2) / 3 * 4) { "圖片附件過大" }
    val bytes = Base64.getDecoder().decode(data)
    require(bytes.size <= NOTE_IMAGE_MAX_BYTES && Base64.getEncoder().encodeToString(bytes) == data && imageHash(bytes) == id) { "圖片附件損壞或校驗值不符" }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outMimeType == "image/jpeg" && options.outWidth in 1..NOTE_IMAGE_MAX_EDGE && options.outHeight in 1..NOTE_IMAGE_MAX_EDGE) { "圖片格式或尺寸不支援" }
    return bytes
}

/** Only the exact content URIs selected by the user are read; originals are never modified. */
class NoteImages(private val db: TodoDatabase) {
    suspend fun import(context: Context, uris: List<Uri>, existing: List<String>): List<String> = withContext(Dispatchers.IO) {
        require(existing.size + uris.size <= MAX_NOTE_IMAGES) { "每項備註最多可加入 $MAX_NOTE_IMAGES 張圖片" }
        // Decode every file first so failure never partially changes the editor selection.
        val rows = uris.map { uri ->
            require(uri.scheme == "content") { "請透過系統選圖器選取圖片" }
            val raw = requireNotNull(context.contentResolver.openInputStream(uri)) { "無法讀取所選圖片" }.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    require(out.size() + n <= 25 * 1024 * 1024) { "原始圖片不可超過 25 MiB" }; out.write(buffer, 0, n) }
                out.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "無法讀取圖片，或原始尺寸過大" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > NOTE_IMAGE_MAX_EDGE * 2) sample *= 2
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })) { "無法解碼圖片" }
            var bitmap = decoded
            try {
                val orientation = runCatching { ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                val matrix = Matrix().apply { when (orientation) {
                    2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> { setRotate(180f); postScale(-1f, 1f) }
                    5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f)
                } }
                val ratio = minOf(1f, NOTE_IMAGE_MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
                matrix.postScale(ratio, ratio)
                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (oriented !== bitmap) { bitmap.recycle(); bitmap = oriented }
                // Re-encode pixels only: no original location or EXIF metadata is copied.
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                bitmap.recycle(); bitmap = opaque
                var quality = 90
                var bytes: ByteArray
                do { val out = ByteArrayOutputStream(); check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)); bytes = out.toByteArray(); quality -= 10 }
                while (bytes.size > NOTE_IMAGE_MAX_BYTES && quality >= 40)
                require(bytes.size <= NOTE_IMAGE_MAX_BYTES) { "圖片壓縮後仍過大，請先縮小圖片" }
                NoteImageEntity(imageHash(bytes), Base64.getEncoder().encodeToString(bytes))
            } finally { if (!bitmap.isRecycled) bitmap.recycle() }
        }
        db.withTransaction { rows.forEach { db.noteImages().insert(it) } }
        (existing + rows.map { it.id }).distinct()
    }

    suspend fun bitmap(id: String, thumbnail: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val row = db.noteImages().image(id) ?: return@withContext null
        val bytes = Base64.getDecoder().decode(row.data)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = if (thumbnail) 4 else 1 })
    }

    suspend fun requireImages(encoded: String) { decodeImageIds(encoded).forEach { require(db.noteImages().image(it) != null) { "圖片附件已不存在，請重新選取" } } }

    /** Run after the editor has closed. Keep saved drafts and all historical snapshots. */
    suspend fun prune(draftIds: List<String>) = db.withTransaction {
        val ids = draftIds.toMutableSet()
        (db.dao().allTasks().map { it.imageIds } + db.dao().allSeries().map { it.imageIds }).forEach { ids += decodeImageIds(it) }
        db.dao().allOccurrences().forEach { ids += decodeImageIds(OccurrenceSnapshot.read(it.snapshot)?.imageIds.orEmpty()) }
        db.noteImages().ids().filter { it !in ids }.forEach { db.noteImages().delete(it) }
    }
}
