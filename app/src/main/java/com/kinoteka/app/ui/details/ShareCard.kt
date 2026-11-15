package com.kinoteka.app.ui.details

import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * «Поділитися»: красивая карточка с постером и змейкой-оценкой.
 * Предпросмотр рисуется в слой, который потом сохраняется в картинку и отправляется.
 */
@Composable
fun ShareCardDialog(
    title: String,
    year: String?,
    posterModel: String?,
    rating: Int?,
    note: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Сама карточка (то, что уйдёт картинкой).
            Box(
                Modifier
                    .width(300.dp)
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
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(170.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF213628)),
                    ) {
                        if (posterModel != null) {
                            // Без аппаратных битмапов — иначе картинку не сохранить на старых Android.
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(posterModel).allowHardware(false).build(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    year?.let { Text(it, color = Color(0xFFB7CBB1), style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(10.dp))
                    if (rating != null) {
                        Text("Моя оцінка: $rating / 10", color = Color(0xFFA5D86E), fontWeight = FontWeight.SemiBold)
                        SnakeRating(rating = rating, onRate = {}, onClear = {})
                    } else {
                        Text("Ще не оцінено", color = Color(0xFFB7CBB1))
                    }
                    if (!note.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "«$note»", color = Color(0xFFE2EEDA), fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Кінотека 🐍", color = Color(0xFF83977E), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Закрити", color = Color.White) }
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                val file = withContext(Dispatchers.IO) {
                                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                                    File(dir, "kinoteka_share.png").also { f ->
                                        FileOutputStream(f).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                    }
                                }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "$title — моя оцінка в Кінотеці 🐍")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Поділитися"))
                            }
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Надіслати")
                }
            }
        }
    }
}
