package com.kinoteka.app.data.update

import android.content.Context
import com.kinoteka.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GhAsset> = emptyList(),
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

/** Вышла новая версия: номер, что нового и откуда качать. */
data class AppUpdate(
    val version: String,
    val notes: String,
    /** Прямая ссылка на APK (если он приложен к релизу), иначе страница релиза. */
    val downloadUrl: String,
    val pageUrl: String,
    /** Прямая ссылка на APK для скачивания внутри приложения (null — APK к релизу не приложен). */
    val apkUrl: String? = null,
    val apkSize: Long = 0,
)

/**
 * Проверка обновлений через GitHub Releases (без своего сервера).
 * Сам APK приложение не ставит: открывает ссылку, браузер скачивает, Android предлагает обновить.
 */
class UpdateChecker(context: Context, private val http: OkHttpClient) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val cacheDir = context.cacheDir
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [force] = false — тихая проверка при запуске: не чаще раза в 12 часов
     * и без версий, которые вы попросили пропустить. null — обновлений нет (или нет сети).
     */
    suspend fun check(force: Boolean): AppUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return@withContext null
        val release = runCatching { fetchLatest() }.getOrNull() ?: return@withContext null
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val version = release.tag.removePrefix("v")
        if (release.draft || release.prerelease || !isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        if (!force && prefs.getString(KEY_SKIPPED, null) == version) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        AppUpdate(
            version = version,
            notes = cleanNotes(release.body.orEmpty()),
            downloadUrl = apk?.url ?: release.htmlUrl,
            pageUrl = release.htmlUrl,
            apkUrl = apk?.url,
            apkSize = apk?.size ?: 0,
        )
    }

    /**
     * Скачать APK обновления в кэш приложения. [onProgress] — доля 0..1 (или -1, если размер неизвестен).
     * Проверяем размер: недокачанный файл установщику не отдаём.
     */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): java.io.File = withContext(Dispatchers.IO) {
        val url = update.apkUrl ?: error("no apk")
        val dir = java.io.File(cacheDir, "updates").apply { mkdirs() }
        // Старые скачанные версии больше не нужны.
        dir.listFiles()?.forEach { it.delete() }
        val target = java.io.File(dir, "Kinoteka-${update.version}.apk")
        val request = Request.Builder().url(url).header("User-Agent", "Kinoteka-Android").cacheControl(CacheControl.FORCE_NETWORK).build()
        http.newBuilder().cache(null).build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
            var done = 0L
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 32 * 1024) {
                            lastReport = done
                            onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else -1f)
                        }
                    }
                }
            }
            if (total > 0 && done != total) {
                target.delete()
                throw java.io.IOException("incomplete download")
            }
        }
        onProgress(1f)
        target
    }

    /** «Пропустить эту версию» — больше не напоминать о ней при запуске. */
    fun skip(version: String) = prefs.edit().putString(KEY_SKIPPED, version).apply()

    private fun fetchLatest(): GhRelease? {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Kinoteka-Android")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return json.decodeFromString(GhRelease.serializer(), response.body.string())
        }
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/RzTimi/kinoteka/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

        /** «0.10.0» новее «0.9.3»: сравниваем числа по частям, а не строки. */
        fun isNewer(remote: String, current: String): Boolean {
            val a = parts(remote)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(v: String): List<Int> =
            v.trim().removePrefix("v").substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

        /** Markdown из описания релиза — в простой текст для диалога. */
        fun cleanNotes(md: String): String = md
            .replace("\r", "")
            .replace("**", "")
            .replace("`", "")
            .lines()
            .joinToString("\n") { line -> line.trimEnd().replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "• ") }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
