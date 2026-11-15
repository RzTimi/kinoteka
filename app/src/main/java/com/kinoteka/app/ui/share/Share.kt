package com.kinoteka.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.PosterStorage
import com.kinoteka.app.data.library.formatScore
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Ссылка на страницу на TMDB (на языке приложения). */
fun tmdbUrl(type: MediaType, id: Int): String =
    "https://www.themoviedb.org/${type.path}/$id?language=${I18n.language.tmdb}"

/** Текст про один фильм: название, год, оценка, заметка и ссылка. */
fun movieShareText(
    title: String,
    year: String?,
    type: MediaType,
    id: Int,
    score: Double?,
    note: String?,
    manual: Boolean = false,
): String = buildString {
    append(if (type == MediaType.TV) "📺 " else "🎬 ")
    append(title)
    year?.let { append(" ($it)") }
    score?.let { append(" — 🐍 ${formatScore(it)}/10") }
    note?.takeIf { it.isNotBlank() }?.let { append("\n").append(tr("«$it»", "“$it”", "«$it»")) }
    if (!manual) append("\n").append(tmdbUrl(type, id))
}

/** Текст про список фильмов (подборку). */
fun listShareText(title: String, entries: List<LibraryEntry>): String = buildString {
    val n = entries.size
    append("📁 ").append(title).append(" — ")
    append("$n ${plural(n, "фільм|фільми|фільмів", "title|titles", "фильм|фильма|фильмов")}")
    append("\n")
    entries.forEachIndexed { i, e ->
        append("\n").append(i + 1).append(". ")
        append(e.displayTitle)
        e.year?.let { append(" ($it)") }
        e.score?.let { append(" — 🐍 ${formatScore(it)}") }
        if (!e.manual) append("\n   ").append(tmdbUrl(e.type, e.tmdbId))
    }
    append("\n\n").append(tr("Надіслано з Кінотеки 🐍", "Sent from Kinoteka 🐍", "Отправлено из Кинотеки 🐍"))
}

/** Отправить текст через обычное меню «Поделиться» (Telegram, Viber…). */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, tr("Поділитися", "Share", "Поделиться")))
}

/** Сохранить слой-картинку и отправить. */
suspend fun shareLayer(context: Context, layer: GraphicsLayer, caption: String) {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "kinoteka_share.png").also { f ->
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, tr("Поділитися", "Share", "Поделиться")))
}

/**
 * «Поділитися підбіркою»: коллаж из постеров (картинкой) или список со ссылками (текстом).
 */
@Composable
fun ListShareDialog(title: String, entries: List<LibraryEntry>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    val shown = entries.take(9)
    val rest = entries.size - shown.size
    val columns = if (shown.size <= 4) 2 else 3
    val scores = entries.mapNotNull { it.score }

    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Коллаж — то, что уйдёт картинкой.
            Box(
                Modifier
                    .width(320.dp)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF1B3A22), Color(0xFF0D1A10))))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "📁 $title", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val n = entries.size
                    val subtitle = buildString {
                        append("$n ${plural(n, "фільм|фільми|фільмів", "title|titles", "фильм|фильма|фильмов")}")
                        if (scores.isNotEmpty()) {
                            append(tr(" · моя середня 🐍 ", " · my average 🐍 ", " · моя средняя 🐍 "))
                            append(formatScore(Math.round(scores.average() * 10) / 10.0))
                        }
                    }
                    Text(subtitle, color = Color(0xFFB7CBB1), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    shown.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Неполный ряд — по центру, постеры того же размера.
                            val pad = columns - row.size
                            if (pad > 0) Spacer(Modifier.weight(pad / 2f))
                            row.forEach { e ->
                                val isLast = e == shown.last() && rest > 0
                                CollagePoster(e, plus = if (isLast) rest else 0, modifier = Modifier.weight(1f))
                            }
                            if (pad > 0) Spacer(Modifier.weight(pad / 2f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(tr("Кінотека 🐍", "Kinoteka 🐍", "Кинотека 🐍"), color = Color(0xFF83977E), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            ShareButtons(
                onText = { shareText(context, listShareText(title, entries)) },
                onImage = {
                    busy = true
                    scope.launch {
                        runCatching {
                            shareLayer(context, layer, tr("Моя підбірка «$title» у Кінотеці 🐍", "My collection “$title” on Kinoteka 🐍", "Моя подборка «$title» в Кинотеке 🐍"))
                        }
                        busy = false
                    }
                },
                imageEnabled = !busy,
                onClose = onDismiss,
            )
        }
    }
}

/** Цвет второстепенных кнопок: сплошной серо-зелёный, чтобы фон под диалогом не просвечивал. */
private val SecondaryButtonColor = Color(0xFF3A4A3D)

/**
 * Кнопки под карточкой «Поделиться»: «Текстом», «Картинкой» и «Закрыть».
 * Все с непрозрачным фоном — под диалогом бывает текст.
 */
@Composable
fun ShareButtons(onText: (() -> Unit)?, onImage: () -> Unit, imageEnabled: Boolean, onClose: () -> Unit) {
    val secondary = ButtonDefaults.buttonColors(containerColor = SecondaryButtonColor, contentColor = Color.White)
    val padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    Column(Modifier.width(320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onText != null) {
                Button(onClick = onText, modifier = Modifier.weight(1f), colors = secondary, contentPadding = padding) {
                    Icon(Icons.AutoMirrored.Filled.ShortText, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Текстом", "As text", "Текстом"), maxLines = 1, softWrap = false)
                }
            }
            Button(onClick = onImage, enabled = imageEnabled, modifier = Modifier.weight(1f), contentPadding = padding) {
                Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Картинкою", "As image", "Картинкой"), maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose, colors = secondary, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
            Text(tr("Закрити", "Close", "Закрыть"))
        }
    }
}

@Composable
private fun CollagePoster(e: LibraryEntry, plus: Int, modifier: Modifier) {
    val context = LocalContext.current
    val model = remember(e.key) { PosterStorage.model(context.filesDir, e.customPoster, e.posterPath, "w342") }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF213628))) {
            if (model != null) {
                // Без аппаратных битмапов — иначе картинку не сохранить.
                AsyncImage(
                    model = ImageRequest.Builder(context).data(model).allowHardware(false).build(),
                    contentDescription = e.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    e.displayTitle, color = Color(0xFFB7CBB1), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(4.dp), maxLines = 4,
                )
            }
            e.score?.let { s ->
                Text(
                    "🐍 ${formatScore(s)}",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xFF2E7D32)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (plus > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Text("+$plus", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
