package com.kinoteka.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.kinoteka.app.data.tmdb.TmdbImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Свои постеры из галереи: хранятся в files/posters (потом уйдут на Google Диск в папку posters/). */
class PosterStorage(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR).apply { mkdirs() }

    /** Загрузить выбранную картинку, уменьшив до разумного размера (с учётом поворота фото). */
    suspend fun decode(uri: Uri, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = maxSide.toFloat() / max(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }.getOrNull()
    }

    /** Вырезать область 2:3, уменьшить до 600x900 и сохранить. Возвращает ссылку "file:имя.jpg". */
    suspend fun saveCropped(src: Bitmap, rect: Rect, key: String): String = withContext(Dispatchers.IO) {
        val safe = Rect(
            rect.left.coerceIn(0, src.width - 1),
            rect.top.coerceIn(0, src.height - 1),
            rect.right.coerceIn(1, src.width),
            rect.bottom.coerceIn(1, src.height),
        )
        val cropped = Bitmap.createBitmap(src, safe.left, safe.top, safe.width(), safe.height())
        val targetW = minOf(600, cropped.width)
        val targetH = targetW * 3 / 2
        val scaled = if (cropped.width != targetW || cropped.height != targetH) {
            Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        } else cropped
        // Время в имени файла: чтобы кэш картинок не показывал старую версию.
        val name = "${key.replace(':', '_')}_${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(dir, name)).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        "$FILE_PREFIX$name"
    }

    /** Файл постера по имени (без префикса "file:"). */
    fun localFile(name: String): File = File(dir, name)

    /** Сохранить постер, скачанный с Google Диска. */
    suspend fun writeDownloaded(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(dir, name).writeBytes(bytes)
    }

    /** Удалить файл, если это постер из галереи. */
    fun deleteIfLocal(ref: String?) {
        if (ref != null && ref.startsWith(FILE_PREFIX)) File(dir, ref.removePrefix(FILE_PREFIX)).delete()
    }

    companion object {
        const val DIR = "posters"
        const val FILE_PREFIX = "file:"
        const val TMDB_PREFIX = "tmdb:"

        /** Что показывать в постере: свой (TMDB или файл) или оригинальный. */
        fun model(filesDir: File, custom: String?, tmdbPath: String?, size: String): String? = when {
            custom?.startsWith(TMDB_PREFIX) == true -> TmdbImages.poster(custom.removePrefix(TMDB_PREFIX), size)
            custom?.startsWith(FILE_PREFIX) == true ->
                "file://" + File(File(filesDir, DIR), custom.removePrefix(FILE_PREFIX)).absolutePath
            else -> TmdbImages.poster(tmdbPath, size)
        }
    }
}
