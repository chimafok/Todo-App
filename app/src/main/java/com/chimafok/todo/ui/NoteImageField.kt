package com.chimafok.todo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import com.chimafok.todo.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chimafok.todo.data.NoteImages
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.launch

@Composable
internal fun NoteImageField(encoded: String, enabled: Boolean, onChange: (String) -> Unit,
    onBusy: (Boolean) -> Unit, onPickerActive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val images = remember { NoteImages(ReminderRuntime.database) }
    val scope = rememberCoroutineScope()
    val ids = decodeImageIds(encoded)
    var importing by remember { mutableStateOf(false) }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val currentIds by rememberUpdatedState(ids)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        pickerPending = false; onPickerActive(false)
        if (uris.isNotEmpty()) {
            importing = true; onBusy(true); error = null
            scope.launch {
                try { onChange(encodeImageIds(images.import(context, uris, currentIds))) }
                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: "圖片未能加入，請重試。" }
                finally { importing = false; onBusy(false) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NoteImageGallery(encoded, onRemove = if (enabled && !importing && !pickerPending) { id -> onChange(encodeImageIds(ids - id)) } else null)
        OutlinedButton(enabled = enabled && !importing && !pickerPending,
            modifier = Modifier.testTag("note_add_images"), onClick = {
                error = null; pickerPending = true; onPickerActive(true)
                try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (e: Exception) { pickerPending = false; onPickerActive(false); error = "無法開啟系統選圖器，請稍後重試。" }
            }) { Text(if (importing) "處理圖片中…" else "加入圖片") }
        Text("圖片保存在本機；會縮小並壓縮，點縮圖可查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("note_image_error")) }
    }
}

@Composable
internal fun NoteImageGallery(encoded: String, onRemove: ((String) -> Unit)? = null) {
    val ids = remember(encoded) { decodeImageIds(encoded) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    if (ids.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().height(104.dp).testTag("note_images"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(ids, key = { _, id -> id }) { index, id ->
            Box(Modifier.size(104.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, modifier = Modifier.size(104.dp).clickable { viewing = id }.testTag("note_image_$index")) {
                    NoteImage(id, true, "圖片 ${index + 1}，點擊放大", Modifier.fillMaxSize())
                }
                onRemove?.let { remove ->
                    IconButton(onClick = { remove(id) }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp).testTag("note_image_remove_$index")) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                            Icon(painterResource(R.drawable.ic_close), "移除圖片 ${index + 1}",
                                Modifier.size(28.dp).padding(5.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
    viewing?.takeIf { it in ids }?.let { id ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("圖片 ${ids.indexOf(id) + 1}/${ids.size}", Modifier.weight(1f).padding(16.dp))
                        TextButton(onClick = { viewing = null }, modifier = Modifier.testTag("note_image_close")) { Text("關閉") }
                    }
                    key(id) {
                        var scale by remember { mutableFloatStateOf(1f) }
                        var x by remember { mutableFloatStateOf(0f) }; var y by remember { mutableFloatStateOf(0f) }
                        val transform = rememberTransformableState { _, zoom, pan, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale == 1f) { x = 0f; y = 0f } else { x = (x + pan.x).coerceIn(-2000f, 2000f); y = (y + pan.y).coerceIn(-2000f, 2000f) }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().transformable(transform), contentAlignment = Alignment.Center) {
                            NoteImage(id, false, "圖片 ${ids.indexOf(id) + 1}", Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = x; translationY = y })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(enabled = ids.indexOf(id) > 0, onClick = { viewing = ids[ids.indexOf(id) - 1] }) { Text("上一張") }
                        TextButton(enabled = ids.indexOf(id) < ids.lastIndex, onClick = { viewing = ids[ids.indexOf(id) + 1] }) { Text("下一張") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteImage(id: String, thumbnail: Boolean, description: String, modifier: Modifier) {
    val images = remember { NoteImages(ReminderRuntime.database) }
    var failed by remember(id, thumbnail) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, id, thumbnail) {
        try { value = images.bitmap(id, thumbnail); failed = value == null }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failed = true }
    }
    val loaded = bitmap
    if (loaded != null) Image(loaded.asImageBitmap(), description, modifier, contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { Text(if (failed) "圖片無法載入" else "載入圖片…", style = MaterialTheme.typography.bodySmall) }
}
