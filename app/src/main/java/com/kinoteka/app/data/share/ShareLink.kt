package com.kinoteka.app.data.share

import android.net.Uri
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Один фильм в ссылке «Открыть в Кінотеці». Короткие имена — чтобы ссылка была короче. */
@Serializable
data class SharedItem(
    /** "m" — фильм, "t" — сериал. */
    @SerialName("k") val kind: String,
    @SerialName("id") val id: Int,
    @SerialName("n") val title: String,
    @SerialName("y") val year: String? = null,
    @SerialName("p") val poster: String? = null,
    /** Раздел (MOVIES, ANIME…) — чтобы у получателя фильм попал куда надо. */
    @SerialName("s") val section: String? = null,
    /** Оценка отправителя. */
    @SerialName("r") val score: Double? = null,
) {
    val type: MediaType get() = if (kind == "t") MediaType.TV else MediaType.MOVIE
}

/** Что отправили: один фильм (title = null) или подборка. */
@Serializable
data class SharedPayload(
    @SerialName("v") val version: Int = 1,
    @SerialName("t") val title: String? = null,
    @SerialName("i") val items: List<SharedItem>,
) {
    val isSingle: Boolean get() = title == null && items.size == 1
}

/**
 * Ссылки «Открыть в Кінотеці».
 * Состав сжимается (deflate) и кладётся в ссылку целиком — сервер и база не нужны.
 * Страница на GitHub Pages показывает список и кнопку, которая открывает приложение.
 */
object ShareLink {
    const val PAGE = "https://rztimi.github.io/kinoteka/open/"
    const val SCHEME = "kinoteka"
    const val HOST = "open"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun item(e: LibraryEntry): SharedItem = SharedItem(
        kind = if (e.type == MediaType.TV) "t" else "m",
        id = e.tmdbId,
        title = e.displayTitle,
        year = e.year,
        poster = e.posterPath,
        section = e.section,
        score = e.score,
    )

    fun item(type: MediaType, id: Int, title: String, year: String?, poster: String?, section: Section?, score: Double?) = SharedItem(
        kind = if (type == MediaType.TV) "t" else "m",
        id = id, title = title, year = year, poster = poster, section = section?.name, score = score,
    )

    /** Ссылка на страницу (её и отправляем). Фильмы, добавленные вручную, в ссылку не попадают. */
    fun url(payload: SharedPayload): String = PAGE + "#d=" + encode(payload)

    fun encode(payload: SharedPayload): String {
        val bytes = json.encodeToString(SharedPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bytes); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    fun decode(data: String): SharedPayload? = runCatching {
        val bytes = java.util.Base64.getUrlDecoder().decode(data)
        val inflater = Inflater(true).apply { setInput(bytes) }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        json.decodeFromString(SharedPayload.serializer(), out.toString(Charsets.UTF_8.name()))
    }.getOrNull()?.takeIf { it.items.isNotEmpty() }

    /**
     * Достать данные из ссылки, которой открыли приложение:
     * kinoteka://open?d=… (кнопка на странице) или https://…/kinoteka/open/#d=… (сама ссылка).
     */
    fun fromUri(uri: Uri?): String? {
        uri ?: return null
        val d = when (uri.scheme) {
            SCHEME -> uri.getQueryParameter("d")
            "https", "http" -> uri.encodedFragment?.removePrefix("d=")?.substringBefore('&')
                ?: uri.getQueryParameter("d")
            else -> null
        }
        return d?.takeIf { it.isNotBlank() && decode(it) != null }
    }
}
