package com.kinoteka.app.data

import com.kinoteka.app.data.tmdb.Genre
import com.kinoteka.app.data.tmdb.TmdbApi
import com.kinoteka.app.data.tmdb.TmdbDetails
import com.kinoteka.app.data.tmdb.TmdbItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.UnknownHostException

/** Карточка фильма/сериала для списков. */
data class MediaCard(
    val id: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String?,
    val year: String?,
    val rating: Double,
    val section: Section,
) {
    val key: String get() = mediaKey(type, id)
}

fun mediaKey(type: MediaType, id: Int) = "${type.path}:$id"

data class PageResult(val items: List<MediaCard>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

class MediaRepository(private val api: TmdbApi) {

    private val genreCache = mutableMapOf<MediaType, List<Genre>>()
    private val genreMutex = Mutex()

    /** Одна страница discover для раздела, с фильтром по разделу на нашей стороне. */
    suspend fun discover(section: Section, type: MediaType, extra: Map<String, String>, page: Int): PageResult {
        val params = mergeParams(section.baseParams(type), extra) + ("page" to page.toString())
        val response = api.discover(type.path, params)
        val filtered = response.results.filter { Section.classify(it, type) == section }
        val titled = withReadableTitles(filtered, { type }) { api.discover(type.path, params + ("language" to "en-US")).results }
        return PageResult(titled.map { it.toCard(type) }, response.page, minOf(response.totalPages, 500))
    }

    /**
     * Если у TMDB нет украинского перевода, приходит оригинальное название
     * (иероглифы, тайский…). Для таких берём английское название.
     */
    private suspend fun withReadableTitles(
        items: List<TmdbItem>,
        typeOf: (TmdbItem) -> MediaType,
        loadEnglish: suspend () -> List<TmdbItem>,
    ): List<TmdbItem> {
        if (items.all { it.displayName(typeOf(it)).isReadable() }) return items
        val english = runCatching { loadEnglish() }.getOrNull()?.associateBy { it.id } ?: return items
        return items.map { item ->
            val type = typeOf(item)
            val en = english[item.id]?.displayName(type)
            if (!item.displayName(type).isReadable() && en != null && en.isReadable()) {
                if (type == MediaType.MOVIE) item.copy(title = en) else item.copy(name = en)
            } else item
        }
    }

    /** Лента для главной: добираем страницы, пока не наберётся достаточно карточек. */
    suspend fun feed(section: Section, feed: Feed, minItems: Int = 14, maxPages: Int = 3): List<MediaCard> {
        val result = LinkedHashMap<String, MediaCard>()
        var page = 1
        while (page <= maxPages) {
            val r = discover(section, feed.type, feed.params, page)
            // На главной показываем только то, что с постером: так ленты выглядят аккуратно.
            r.items.filter { it.posterPath != null }.forEach { result.putIfAbsent(it.key, it) }
            if (result.size >= minItems || !r.hasMore) break
            page++
        }
        return result.values.toList()
    }

    suspend fun genres(section: Section, type: MediaType): List<Genre> {
        val all = genreMutex.withLock {
            genreCache[type] ?: api.genres(type.path).genres.also { genreCache[type] = it }
        }
        return all.filter { it.id !in TmdbGenres.hiddenInGenreList && it.id != section.fixedGenre }
    }

    suspend fun search(query: String, page: Int): PageResult {
        val response = api.searchMulti(query, page)
        val media = response.results.filter { MediaType.fromPath(it.mediaType) != null }
        val typeOf = { item: TmdbItem -> MediaType.fromPath(item.mediaType)!! }
        val titled = withReadableTitles(media, typeOf) { api.searchMulti(query, page, language = "en-US").results }
        return PageResult(titled.map { it.toCard(typeOf(it)) }, response.page, response.totalPages)
    }

    suspend fun details(type: MediaType, id: Int): TmdbDetails {
        val uk = api.details(type.path, id)
        val ukTitle = (if (type == MediaType.MOVIE) uk.title ?: uk.name else uk.name ?: uk.title).orEmpty()
        val needOverview = uk.overview.isNullOrBlank()
        val needTitle = !ukTitle.isReadable()
        if (!needOverview && !needTitle) return uk
        // Нет украинского описания или названия: подставляем английское.
        val en = runCatching { api.details(type.path, id, append = "", language = "en-US") }.getOrNull() ?: return uk
        var result = uk
        if (needOverview && !en.overview.isNullOrBlank()) result = result.copy(overview = en.overview)
        if (needTitle) {
            val enTitle = if (type == MediaType.MOVIE) en.title else en.name
            if (!enTitle.isNullOrBlank() && enTitle.isReadable()) {
                result = if (type == MediaType.MOVIE) result.copy(title = enTitle) else result.copy(name = enTitle)
            }
        }
        return result
    }

    companion object {
        /** Склеивает параметры; with_genres объединяет через запятую (И). */
        fun mergeParams(base: Map<String, String>, extra: Map<String, String>): Map<String, String> {
            val out = base.toMutableMap()
            for ((k, v) in extra) {
                out[k] = if (k == "with_genres" && out[k] != null) "${out[k]},$v" else v
            }
            return out
        }
    }
}

fun TmdbItem.displayName(type: MediaType): String =
    (if (type == MediaType.MOVIE) title ?: name else name ?: title) ?: originalTitle ?: originalName ?: "—"

/** Название читается, если в нём только латиница/кириллица (без иероглифов, тайского и т.п.). */
fun String.isReadable(): Boolean = none { it.isLetter() && it.code >= 0x0530 }

fun TmdbItem.toCard(type: MediaType) = MediaCard(
    id = id,
    type = type,
    title = displayName(type),
    posterPath = posterPath,
    year = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.isNotBlank() },
    rating = voteAverage,
    section = Section.classify(this, type),
)

fun Throwable.friendlyMessage(): String = when (this) {
    is UnknownHostException -> "Немає з'єднання з інтернетом"
    is retrofit2.HttpException -> when (code()) {
        401 -> "Ключ TMDB не підходить. Перевірте TMDB_API_KEY у local.properties"
        404 -> "Не знайдено"
        429 -> "Забагато запитів, спробуйте за хвилину"
        else -> "Помилка сервера (${code()})"
    }
    is IOException -> "Проблема з мережею. Спробуйте ще раз"
    else -> message ?: "Щось пішло не так"
}
