package com.chimafok.todo.ui

import com.chimafok.todo.i18n.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: tr(R.string.text_cannot_add_the_image_please_try_cad937) }
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
                catch (e: Exception) { pickerPending = false; onPickerActive(false); error = tr(R.string.text_cannot_open_the_system_photo_picker_3bf025) }
            }) { Text(if (importing) tr(R.string.text_processing_images_90486f) else tr(R.string.text_add_images_970ae4)) }
        Text(tr(R.string.text_images_are_stored_locally_resized_and_571608), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    NoteImage(id, true, tr(R.string.text_image_s_tap_to_enlarge_87f152, index + 1), Modifier.fillMaxSize())
                }
                onRemove?.let { remove ->
                    IconButton(onClick = { remove(id) }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp).testTag("note_image_remove_$index")) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                            Icon(painterResource(R.drawable.ic_close), tr(R.string.text_remove_image_s_905d90, index + 1),
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
                        Text(tr(R.string.text_image_s_s_cae5c2, ids.indexOf(id) + 1, ids.size), Modifier.weight(1f).padding(16.dp))
                        TextButton(onClick = { viewing = null }, modifier = Modifier.testTag("note_image_close")) { Text(tr(R.string.text_close_c7fddd)) }
                    }
                    key(id) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            NoteImage(id, false, tr(R.string.text_image_s_911ad3, ids.indexOf(id) + 1),
                                Modifier.fillMaxSize(), onBackgroundTap = { viewing = null })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(enabled = ids.indexOf(id) > 0, onClick = { viewing = ids[ids.indexOf(id) - 1] }) { Text(tr(R.string.text_previous_image_a3b6d8)) }
                        TextButton(enabled = ids.indexOf(id) < ids.lastIndex, onClick = { viewing = ids[ids.indexOf(id) + 1] }) { Text(tr(R.string.text_next_image_c3b16d)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteImage(id: String, thumbnail: Boolean, description: String, modifier: Modifier, onBackgroundTap: (() -> Unit)? = null) {
    val images = remember { NoteImages(ReminderRuntime.database) }
    var failed by remember(id, thumbnail) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, id, thumbnail) {
        try { value = images.bitmap(id, thumbnail); failed = value == null }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failed = true }
    }
    val loaded = bitmap
    if (loaded != null && onBackgroundTap != null) NoteImagePreview(loaded.asImageBitmap(), description, modifier, onBackgroundTap)
    else if (loaded != null) Image(loaded.asImageBitmap(), description, modifier, contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier, contentAlignment = Alignment.Center) { Text(if (failed) tr(R.string.text_cannot_load_image_845573) else tr(R.string.text_loading_image_c763a6), style = MaterialTheme.typography.bodySmall) }
}
