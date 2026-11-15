package com.kinoteka.app.data

import com.kinoteka.app.data.tmdb.Genre
import com.kinoteka.app.data.tmdb.ImageInfo
import com.kinoteka.app.data.tmdb.TmdbApi
import com.kinoteka.app.data.tmdb.TmdbDetails
import com.kinoteka.app.data.tmdb.TmdbItem
import com.kinoteka.app.data.tmdb.PersonDetails
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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

/** Фильм/сериал в фильмографии актёра: карточка + роль. */
data class PersonCredit(val card: MediaCard, val role: String?, val date: String?)

/** Страница актёра. */
data class PersonPage(val person: PersonDetails, val knownFor: List<MediaCard>, val credits: List<PersonCredit>)

class MediaRepository(
    private val api: TmdbApi,
    private val http: OkHttpClient = OkHttpClient(),
    private val omdbKey: String = "",
) {
    val hasOmdb: Boolean get() = omdbKey.isNotBlank()

    /** Сбросить кэш (после смены языка данных). */
    fun clearCaches() = genreCache.clear()

    /** Рейтинг IMDb через OMDb (нужен бесплатный ключ). Возвращает «8.2» и число голосов. */
    suspend fun imdbRating(imdbId: String): Pair<String, String>? {
        if (omdbKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://www.omdbapi.com/".toHttpUrl().newBuilder()
                    .addQueryParameter("i", imdbId)
                    .addQueryParameter("apikey", omdbKey)
                    .build()
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val obj = Json.parseToJsonElement(resp.body.string()).jsonObject
                    val rating = obj["imdbRating"]?.jsonPrimitive?.content
                    val votes = obj["imdbVotes"]?.jsonPrimitive?.content.orEmpty()
                    if (rating == null || rating == "N/A") null else rating to votes
                }
            }.getOrNull()
        }
    }

    /** Лёгкий запрос деталей (без актёров, видео и т.п.) — для напоминаний. */
    suspend fun detailsLite(type: MediaType, id: Int): TmdbDetails = api.details(type.path, id, append = "")

    /** Поиск фильма по названию и году — для импорта. */
    suspend fun findMovie(title: String, year: Int?): TmdbItem? =
        api.searchMovie(title, year).results.firstOrNull()
            ?: if (year != null) api.searchMovie(title, null).results.firstOrNull() else null

    /** Сезон: серии. Если у серий нет описаний на украинском — подставляем английские. */
    suspend fun season(tvId: Int, season: Int): com.kinoteka.app.data.tmdb.SeasonDetails {
        val uk = api.season(tvId, season)
        if (uk.episodes.none { it.overview.isNullOrBlank() }) return uk
        val en = runCatching { api.season(tvId, season, language = "en-US") }.getOrNull() ?: return uk
        val enByNumber = en.episodes.associateBy { it.number }
        return uk.copy(episodes = uk.episodes.map { ep ->
            val e = enByNumber[ep.number]
            ep.copy(
                overview = ep.overview?.takeIf { it.isNotBlank() } ?: e?.overview,
                name = ep.name?.takeIf { it.isNotBlank() && !it.matches(Regex("(Епізод|Серія|Эпизод|Серия|Episode) \\d+")) } ?: e?.name ?: ep.name,
            )
        })
    }

    /** Актёр: биография (если нет на украинском — английская), известные работы и фильмография. */
    suspend fun person(id: Int): PersonPage {
        var person = api.person(id)
        if (person.biography.isNullOrBlank()) {
            val en = runCatching { api.person(id, append = "", language = "en-US") }.getOrNull()
            if (!en?.biography.isNullOrBlank()) person = person.copy(biography = en?.biography)
        }
        val credits = person.credits ?: com.kinoteka.app.data.tmdb.PersonCredits()
        // Убираем ток-шоу, новости и реалити, где человек «играет себя».
        fun relevant(item: TmdbItem): Boolean {
            val selfRole = item.character?.let { c -> listOf("Self", "Himself", "Herself", "себе").any { c.contains(it, ignoreCase = true) } } == true
            return item.genreIds.none { it == TmdbGenres.TALK || it == TmdbGenres.NEWS || it == TmdbGenres.REALITY } && !selfRole
        }
        val crewJobs = setOf("Director", "Screenplay", "Writer", "Creator", "Novel", "Story")
        val all = (credits.cast.filter(::relevant).map { it to it.character } +
            credits.crew.filter { it.job in crewJobs && relevant(it) }.map { it to jobLabel(it.job) })
            .mapNotNull { (item, role) ->
                val type = MediaType.fromPath(item.mediaType) ?: return@mapNotNull null
                PersonCredit(item.toCard(type), role?.takeIf { it.isNotBlank() }, item.releaseDate ?: item.firstAirDate)
            }
            .groupBy { it.card.key }
            .map { (_, list) -> list.first().copy(role = list.mapNotNull { it.role }.distinct().joinToString(", ").ifBlank { null }) }
            .sortedByDescending { it.date.orEmpty() }
        val knownFor = (credits.cast + credits.crew).filter(::relevant)
            .sortedByDescending { it.voteCount }
            .mapNotNull { item -> MediaType.fromPath(item.mediaType)?.let { item.toCard(it) } }
            .filter { it.posterPath != null }
            .distinctBy { it.key }
            .take(15)
        return PersonPage(person, knownFor, all)
    }

    private fun jobLabel(job: String?) = when (job) {
        "Director" -> tr("Режисер", "Director", "Режиссёр")
        "Screenplay", "Writer", "Story" -> tr("Сценарист", "Writer", "Сценарист")
        "Creator" -> tr("Автор ідеї", "Creator", "Автор идеи")
        "Novel" -> tr("Автор книги", "Novel", "Автор книги")
        else -> job
    }

    /**
     * Кандидаты для рулетки: популярные фильмы раздела со случайной страницы,
     * с фильтром по жанру и длительности (в минутах).
     */
    suspend fun rouletteCandidates(section: Section, type: MediaType, genreId: Int?, runtime: IntRange?): List<MediaCard> {
        val extra = buildMap {
            put("sort_by", "popularity.desc")
            put("vote_count.gte", if (section.isBase) "150" else "20")
            genreId?.let { put("with_genres", it.toString()) }
            runtime?.let {
                put("with_runtime.gte", it.first.toString())
                if (it.last < 1000) put("with_runtime.lte", it.last.toString())
            }
        }
        val first = discover(section, type, extra, 1)
        val maxPage = first.totalPages.coerceIn(1, 8)
        val page = if (maxPage > 1) (1..maxPage).random() else 1
        val result = if (page == 1) first else discover(section, type, extra, page)
        return (result.items.ifEmpty { first.items }).filter { it.posterPath != null }
    }

    private val genreCache = mutableMapOf<MediaType, List<Genre>>()
    private val genreMutex = Mutex()

    /** Одна страница discover для раздела, с фильтром по разделу на нашей стороне. */
    suspend fun discover(section: Section, type: MediaType, extra: Map<String, String>, page: Int): PageResult {
        // «В тренде» — отдельный список TMDB, не discover.
        extra[Feed.TRENDING]?.let { window ->
            val response = api.trending(type.path, window, page)
            val filtered = response.results.filter { section.accepts(it, type) }
            val titled = withReadableTitles(filtered, { type }) { api.trending(type.path, window, page, language = "en-US").results }
            return PageResult(titled.map { it.toCard(type) }, response.page, minOf(response.totalPages, 500))
        }
        // Явно выбранный «исключаемый» жанр (мультфильм в сериалах и т.п.) снимает исключение раздела.
        val chosen = extra["with_genres"].orEmpty().split(',', '|').mapNotNull { it.trim().toIntOrNull() }
        val overrides = chosen.filter { it in TmdbGenres.overriding }.toSet()
        val base = section.baseParams(type).let { b ->
            val without = b["without_genres"] ?: return@let b
            val left = without.split(',').filter { it.toIntOrNull() !in overrides }
            if (left.isEmpty()) b - "without_genres" else b + ("without_genres" to left.joinToString(","))
        }
        val params = mergeParams(base, extra) + ("page" to page.toString())
        val response = api.discover(type.path, params)
        val filtered = if (overrides.isEmpty()) response.results.filter { section.accepts(it, type) } else response.results
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
    suspend fun feed(section: Section, feed: Feed, minItems: Int = 14, maxPages: Int = 2): List<MediaCard> {
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

    /**
     * Все плашки для экрана жанров: обычные жанры TMDB, подборки по темам (для сериалов)
     * и «другое» — мультфильм, документальный, реалити, новости, ток-шоу.
     */
    suspend fun genres(section: Section, type: MediaType): List<GenreOption> {
        val all = genreMutex.withLock {
            genreCache[type] ?: api.genres(type.path).genres.also { genreCache[type] = it }
        }
        val available = all.filter { it.id != section.fixedGenre }
        val regular = available.filter { it.id !in TmdbGenres.overriding }
            .map { GenreOption(it.name.capitalized(), mapOf("with_genres" to it.id.toString()), GenreOption.Kind.GENRE) }
        val keywords = if (type == MediaType.TV) TmdbGenres.tvKeywordGenres.map { (name, ids) ->
            GenreOption(name, mapOf("with_keywords" to ids), GenreOption.Kind.KEYWORD)
        } else emptyList()
        val extras = available.filter { it.id in TmdbGenres.overriding }
            .map { GenreOption(it.name.capitalized(), mapOf("with_genres" to it.id.toString()), GenreOption.Kind.EXTRA) }
        return regular + keywords + extras
    }

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }

    suspend fun search(query: String, page: Int): PageResult {
        val response = api.searchMulti(query, page)
        val media = response.results.filter { MediaType.fromPath(it.mediaType) != null }
        val typeOf = { item: TmdbItem -> MediaType.fromPath(item.mediaType)!! }
        val titled = withReadableTitles(media, typeOf) { api.searchMulti(query, page, language = "en-US").results }
        return PageResult(titled.map { it.toCard(typeOf(it)) }, response.page, response.totalPages)
    }

    /** Альтернативные постеры: сначала украинские, потом без текста, потом английские. */
    suspend fun posters(type: MediaType, id: Int): List<ImageInfo> =
        api.images(type.path, id).posters
            .filter { it.filePath.isNotBlank() }
            .sortedWith(
                compareBy<ImageInfo>({ when (it.language) { I18n.language.code -> 0; null -> 1; "en" -> 2; else -> 3 } })
                    .thenByDescending { it.voteAverage }
            )

    /** Альтернативные фоны (кадры): сначала без текста, потом остальные. */
    suspend fun backdrops(type: MediaType, id: Int): List<ImageInfo> =
        api.images(type.path, id, languages = "null,${I18n.language.code},en").backdrops
            .filter { it.filePath.isNotBlank() }
            .sortedWith(compareBy<ImageInfo>({ if (it.language == null) 0 else 1 }).thenByDescending { it.voteAverage })
            .take(60)

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
        /**
         * Склеивает параметры: with_genres объединяет через запятую (И);
         * для границ «.lte» берёт более строгую (меньшую), для «.gte» — большую.
         * Так, например, «Класика» (до 1990) не сломается сортировкой «Нові».
         */
        fun mergeParams(base: Map<String, String>, extra: Map<String, String>): Map<String, String> {
            val out = base.toMutableMap()
            for ((k, v) in extra) {
                val old = out[k]
                out[k] = when {
                    old == null -> v
                    k == "with_genres" -> "$old,$v"
                    k.endsWith(".lte") -> if (compareParam(v, old) < 0) v else old
                    k.endsWith(".gte") -> if (compareParam(v, old) > 0) v else old
                    else -> v
                }
            }
            return out
        }

        /** Сравнение значений: числа — как числа, даты (ГГГГ-ММ-ДД) — как строки. */
        private fun compareParam(a: String, b: String): Int {
            val na = a.toDoubleOrNull()
            val nb = b.toDoubleOrNull()
            return if (na != null && nb != null) na.compareTo(nb) else a.compareTo(b)
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
    is UnknownHostException -> tr("Немає з'єднання з інтернетом", "No internet connection", "Нет подключения к интернету")
    is retrofit2.HttpException -> when (code()) {
        401 -> tr("Ключ TMDB не підходить. Перевірте TMDB_API_KEY у local.properties", "The TMDB key is invalid. Check TMDB_API_KEY in local.properties", "Ключ TMDB не подходит. Проверьте TMDB_API_KEY в local.properties")
        404 -> tr("Не знайдено", "Not found", "Не найдено")
        429 -> tr("Забагато запитів, спробуйте за хвилину", "Too many requests, try again in a minute", "Слишком много запросов, попробуйте через минуту")
        else -> tr("Помилка сервера (${code()})", "Server error (${code()})", "Ошибка сервера (${code()})")
    }
    is IOException -> tr("Проблема з мережею. Спробуйте ще раз", "Network problem. Please try again", "Проблема с сетью. Попробуйте ещё раз")
    else -> message ?: tr("Щось пішло не так", "Something went wrong", "Что-то пошло не так")
}
