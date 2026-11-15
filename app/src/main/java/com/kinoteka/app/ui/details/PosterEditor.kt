package com.kinoteka.app.ui.details

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinoteka.app.data.library.PosterStorage
import com.kinoteka.app.data.tmdb.ImageInfo
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.PosterImage
import com.kinoteka.app.ui.components.UiState
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Смена постера: меню из трёх пунктов → галерея (с обрезкой 2:3),
 * другие постеры TMDB или возврат оригинала.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosterEditor(
    open: Boolean,
    onClose: () -> Unit,
    vm: DetailsViewModel,
    title: String,
    originalPoster: String?,
    customPoster: String?,
    /** true — редактируем фон карточки (16:9), а не постер. */
    backdrop: Boolean = false,
) {
    val posterStorage = LocalContainer.current.posters
    val scope = rememberCoroutineScope()
    var showTmdb by remember { mutableStateOf(false) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decoding by remember { mutableStateOf(false) }

    // Системный выбор фото: приложение получает только выбранную картинку, без доступа ко всей галерее.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            decoding = true
            scope.launch {
                cropBitmap = posterStorage.decode(uri)
                decoding = false
            }
        }
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = onClose) {
            Text(
                if (backdrop) "Фон картки" else "Постер",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetItem(
                Icons.Default.PhotoLibrary, "З галереї телефону",
                if (backdrop) "Оберіть фото та обріжте під широкий кадр" else "Оберіть фото та обріжте під постер",
            ) {
                onClose()
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            SheetItem(
                Icons.Default.Collections,
                if (backdrop) "Інші кадри TMDB" else "Інші постери TMDB",
                if (backdrop) "Кадри з фільму без тексту" else "Інші країни, перевидання, без тексту",
            ) {
                onClose()
                if (backdrop) vm.loadAltBackdrops() else vm.loadAltPosters()
                showTmdb = true
            }
            if (customPoster != null) {
                SheetItem(Icons.Default.Restore, "Повернути оригінал", null) {
                    onClose()
                    if (backdrop) vm.resetBackdrop() else vm.resetPoster()
                }
            }
            Spacer(Modifier.padding(bottom = 16.dp).navigationBarsPadding())
        }
    }

    if (decoding) {
        Dialog(onDismissRequest = {}) { CircularProgressIndicator() }
    }

    cropBitmap?.let { bmp ->
        CropDialog(
            bitmap = bmp,
            aspect = if (backdrop) 16f / 9f else 2f / 3f,
            onCancel = { cropBitmap = null },
            onDone = { rect ->
                if (backdrop) vm.saveGalleryBackdrop(bmp, rect) else vm.saveGalleryPoster(bmp, rect)
                cropBitmap = null
            },
        )
    }

    if (showTmdb) {
        TmdbPostersDialog(
            vm = vm,
            title = title,
            selected = customPoster?.removePrefix(PosterStorage.TMDB_PREFIX) ?: originalPoster,
            backdrop = backdrop,
            onPick = { path ->
                if (backdrop) {
                    if (path == originalPoster) vm.resetBackdrop() else vm.useTmdbBackdrop(path)
                } else {
                    if (path == originalPoster) vm.resetPoster() else vm.useTmdbPoster(path)
                }
                showTmdb = false
            },
            onClose = { showTmdb = false },
        )
    }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

private fun languageLabel(lang: String?) = when (lang) {
    "uk" -> "UA"
    "en" -> "EN"
    null -> "Без тексту"
    else -> lang.uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TmdbPostersDialog(
    vm: DetailsViewModel,
    title: String,
    selected: String?,
    backdrop: Boolean,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state by (if (backdrop) vm.altBackdrops else vm.altPosters).collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Обрати постер")
                            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрити") } },
                )
            },
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (val s = state) {
                UiState.Loading -> LoadingBox(modifier)
                is UiState.Error -> ErrorBox(s.message, { vm.loadAltPosters(force = true) }, modifier)
                is UiState.Success -> if (s.data.isEmpty()) {
                    Box(modifier, contentAlignment = Alignment.Center) { Text("Інших постерів немає") }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (backdrop) 220.dp else 120.dp),
                        modifier = modifier,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(s.data, key = { it.filePath }) { img ->
                            if (backdrop) AltBackdrop(img, img.filePath == selected) { onPick(img.filePath) }
                            else AltPoster(img, title, img.filePath == selected) { onPick(img.filePath) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AltPoster(img: ImageInfo, title: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        PosterImage(
            img.filePath, title,
            Modifier.fillMaxWidth().then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier
            ),
        )
        Text(
            if (isSelected) "✓ Обрано" else languageLabel(img.language),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Кадр-фон в списке TMDB. */
@Composable
private fun AltBackdrop(img: ImageInfo, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        coil3.compose.AsyncImage(
            com.kinoteka.app.data.tmdb.TmdbImages.backdrop(img.filePath, "w780"), null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        if (isSelected) Text("✓ Обрано", color = Color.White, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp))
    }
}

/** Обрезка под заданную пропорцию (ширина/высота): постер 2:3 или фон 16:9. */
@Composable
internal fun CropDialog(bitmap: Bitmap, aspect: Float = 2f / 3f, onCancel: () -> Unit, onDone: (Rect) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Text(
                "Перетягніть і збільшіть, щоб вибрати частину для постера",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            var cropRect by remember { mutableStateOf<Rect?>(null) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val cw = with(density) { maxWidth.toPx() }
                val ch = with(density) { maxHeight.toPx() }
                val bw = bitmap.width.toFloat()
                val bh = bitmap.height.toFloat()
                // Рамка 2:3 по центру.
                val frameW = min(cw * 0.9f, ch * 0.9f * aspect)
                val frameH = frameW / aspect
                val frameLeft = (cw - frameW) / 2f
                val frameTop = (ch - frameH) / 2f
                val minScale = max(frameW / bw, frameH / bh)
                var scale by remember(bitmap, cw, ch) { mutableFloatStateOf(minScale) }
                var offset by remember(bitmap, cw, ch) { mutableStateOf(Offset.Zero) }

                fun clampOffset(o: Offset, s: Float): Offset {
                    val maxX = (bw * s - frameW) / 2f
                    val maxY = (bh * s - frameH) / 2f
                    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                }

                // Пересчитываем область обрезки в пикселях исходной картинки.
                LaunchedEffect(scale, offset, cw, ch) {
                    val imgLeft = cw / 2f + offset.x - bw * scale / 2f
                    val imgTop = ch / 2f + offset.y - bh * scale / 2f
                    val x = ((frameLeft - imgLeft) / scale).roundToInt()
                    val y = ((frameTop - imgTop) / scale).roundToInt()
                    val w = (frameW / scale).roundToInt()
                    val h = (frameH / scale).roundToInt()
                    cropRect = Rect(x, y, x + w, y + h)
                }

                Canvas(
                    Modifier.fillMaxSize().pointerInput(bitmap, cw, ch) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, minScale * 5f)
                            scale = newScale
                            offset = clampOffset(offset + pan, newScale)
                        }
                    }
                ) {
                    val iw = bw * scale
                    val ih = bh * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset((cw / 2f + offset.x - iw / 2f).roundToInt(), (ch / 2f + offset.y - ih / 2f).roundToInt()),
                        dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
                    )
                    // Затемняем всё вне рамки.
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addRect(androidx.compose.ui.geometry.Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH))
                    }
                    drawPath(dim, Color.Black.copy(alpha = 0.6f))
                    drawRect(Color.White, Offset(frameLeft, frameTop), Size(frameW, frameH), style = Stroke(width = 2.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onCancel) { Text("Скасувати", color = Color.White) }
                Button(onClick = { cropRect?.let(onDone) }, modifier = Modifier.width(140.dp)) { Text("Готово") }
            }
        }
    }
}
