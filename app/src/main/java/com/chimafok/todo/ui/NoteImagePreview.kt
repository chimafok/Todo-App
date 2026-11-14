package com.chimafok.todo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag

@Composable
internal fun NoteImagePreview(bitmap: ImageBitmap, description: String, modifier: Modifier,
    active: Boolean = true, onZoomChanged: (Boolean) -> Unit = {}, onBackgroundTap: () -> Unit) {
    var scale by remember(active) { mutableFloatStateOf(1f) }
    var pan by remember(active) { mutableStateOf(Offset.Zero) }
    val dismiss by rememberUpdatedState(onBackgroundTap)
    val zoomChanged by rememberUpdatedState(onZoomChanged)
    LaunchedEffect(active) { if (active) zoomChanged(false) }
    val transform = rememberTransformableState { _, zoom, change, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        zoomChanged(scale > 1f)
        pan = if (scale == 1f) Offset.Zero else Offset(
            (pan.x + change.x).coerceIn(-2000f, 2000f), (pan.y + change.y).coerceIn(-2000f, 2000f))
    }
    Box(modifier.testTag(if (active) "note_image_preview" else "note_image_preview_inactive").clipToBounds()
        .transformable(transform, canPan = { scale > 1f }, enabled = active).pointerInput(bitmap, active) {
        detectTapGestures { position ->
            // Image fills the viewport, but ContentScale.Fit leaves letterboxing.
            // Hit-test the painted image, including its current zoom and pan.
            val fit = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
            val halfWidth = bitmap.width * fit * scale / 2f
            val halfHeight = bitmap.height * fit * scale / 2f
            val center = Offset(size.width / 2f, size.height / 2f) + pan
            val image = Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
            if (active && !image.contains(position)) dismiss()
        }
    }) {
        Image(bitmap, description, Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
        }, contentScale = ContentScale.Fit)
    }
}
