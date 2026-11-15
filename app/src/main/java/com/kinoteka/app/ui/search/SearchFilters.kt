package com.kinoteka.app.ui.search

import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.i18n.tr
import java.time.LocalDate

/** Длительность: у фильма — весь фильм, у сериала — одна серия. */
enum class SearchRuntime(private val uk: String, private val en: String, private val ru: String, val min: Int?, val max: Int?) {
    ANY("Будь-яка", "Any", "Любая", null, null),
    SHORT("до 1,5 год", "under 1.5 h", "до 1,5 ч", null, 90),
    MEDIUM("1,5–2 год", "1.5–2 h", "1,5–2 ч", 90, 120),
    LONG("2–3 год", "2–3 h", "2–3 ч", 120, 180),
    EPIC("понад 3 год", "over 3 h", "больше 3 ч", 180, null);

    val label: String get() = tr(uk, en, ru)

    fun matches(minutes: Int): Boolean = (min == null || minutes >= min) && (max == null || minutes <= max)
}

enum class SearchSort(private val uk: String, private val en: String, private val ru: String) {
    RELEVANCE("За збігом", "Best match", "По совпадению"),
    POPULAR("Популярні", "Popular", "Популярные"),
    RATING("За рейтингом", "By rating", "По рейтингу"),
    NEWEST("Спочатку нові", "Newest first", "Сначала новые"),
    OLDEST("Спочатку старі", "Oldest first", "Сначала старые");

    val label: String get() = tr(uk, en, ru)
}

data class SearchFilters(
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val runtime: SearchRuntime = SearchRuntime.ANY,
    /** Минимальный рейтинг TMDB: 6, 7, 8. */
    val minRating: Int? = null,
    /** ID жанров TMDB (фильмовые; для сериалов переводим через [GenreMap]). */
    val genres: Set<Int> = emptySet(),
    val sort: SearchSort = SearchSort.RELEVANCE,
    /** Скрыть то, что уже посмотрено или оценено. */
    val hideSeen: Boolean = false,
) {
    val hasYear: Boolean get() = yearFrom != null || yearTo != null

    /** Сколько фильтров включено (для значка на кнопке). */
    val activeCount: Int
        get() = listOf(hasYear, runtime != SearchRuntime.ANY, minRating != null, genres.isNotEmpty(), sort != SearchSort.RELEVANCE, hideSeen)
            .count { it }

    /** Есть ли что-то, что сужает выдачу (сортировка не считается). */
    val narrows: Boolean
        get() = hasYear || runtime != SearchRuntime.ANY || minRating != null || genres.isNotEmpty() || hideSeen

    val yearLabel: String
        get() = when {
            yearFrom != null && yearTo != null && yearFrom == yearTo -> "$yearFrom"
            yearFrom != null && yearTo != null -> "$yearFrom–$yearTo"
            yearFrom != null -> tr("від $yearFrom", "from $yearFrom", "с $yearFrom")
            yearTo != null -> tr("до $yearTo", "until $yearTo", "до $yearTo")
            else -> tr("Будь-який рік", "Any year", "Любой год")
        }

    /** Проверка на нашей стороне — для поиска по названию (TMDB там фильтров не умеет). */
    fun matches(card: MediaCard, runtime: Int?, library: Map<String, LibraryEntry>): Boolean {
        val year = card.year?.toIntOrNull()
        if (yearFrom != null && (year == null || year < yearFrom)) return false
        if (yearTo != null && (year == null || year > yearTo)) return false
        if (minRating != null && card.rating < minRating) return false
        if (genres.isNotEmpty()) {
            val ids = card.genreIds.toSet()
            val ok = genres.all { g -> GenreMap.forType(g, card.type)?.let { it in ids } == true }
            if (!ok) return false
        }
        if (this.runtime != SearchRuntime.ANY && (runtime == null || !this.runtime.matches(runtime))) return false
        if (hideSeen && isSeen(library[card.key])) return false
        return true
    }

    fun sorted(cards: List<MediaCard>): List<MediaCard> = when (sort) {
        SearchSort.RELEVANCE -> cards
        SearchSort.POPULAR -> cards.sortedByDescending { it.popularity }
        // Оценки с горсткой голосов не в счёт — уходят в конец.
        SearchSort.RATING -> cards.sortedWith(compareByDescending<MediaCard> { it.voteCount >= 20 }.thenByDescending { it.rating })
        SearchSort.NEWEST -> cards.sortedByDescending { it.year ?: "" }
        SearchSort.OLDEST -> cards.sortedBy { it.year ?: "9999" }
    }

    /** Параметры discover — для подбора без названия. null = этот тип не подходит под фильтры. */
    fun discoverParams(type: MediaType, today: LocalDate = LocalDate.now()): Map<String, String>? {
        val genreIds = genres.map { GenreMap.forType(it, type) ?: return null }
        val date = if (type == MediaType.MOVIE) "primary_release_date" else "first_air_date"
        val p = linkedMapOf<String, String>()
        p["vote_count.gte"] = if (sort == SearchSort.RATING || minRating != null) "150" else "20"
        yearFrom?.let { p["$date.gte"] = "$it-01-01" }
        yearTo?.let { p["$date.lte"] = "$it-12-31" }
        runtime.min?.let { p["with_runtime.gte"] = "$it" }
        runtime.max?.let { p["with_runtime.lte"] = "$it" }
        minRating?.let { p["vote_average.gte"] = "$it" }
        if (genreIds.isNotEmpty()) p["with_genres"] = genreIds.distinct().joinToString(",")
        // Ток-шоу, новости и реалити у TMDB тоже «комедии»/«драмы» — в подбор не берём.
        if (type == MediaType.TV) p["without_genres"] = "10763,10764,10767"
        p["sort_by"] = when (sort) {
            SearchSort.RELEVANCE, SearchSort.POPULAR -> "popularity.desc"
            SearchSort.RATING -> "vote_average.desc"
            SearchSort.NEWEST -> "$date.desc"
            SearchSort.OLDEST -> "$date.asc"
        }
        // «Сначала новые» — без будущих премьер.
        if (sort == SearchSort.NEWEST && yearTo == null) p["$date.lte"] = today.toString()
        return p
    }

    companion object {
        fun isSeen(e: LibraryEntry?): Boolean =
            e != null && (e.score != null || e.status == WatchStatus.WATCHED || e.status == WatchStatus.DROPPED)
    }
}

/**
 * У фильмов и сериалов на TMDB разные жанры. Показываем фильмовые, а для сериалов
 * переводим: «Бойовик» и «Пригоди» → «Бойовик і пригоди» и т.д. null — у сериалов такого жанра нет.
 */
object GenreMap {
    private val movieToTv = mapOf(28 to 10759, 12 to 10759, 878 to 10765, 14 to 10765, 10752 to 10768)
    private val shared = setOf(16, 35, 80, 99, 18, 10751, 9648, 37)

    fun forType(movieGenre: Int, type: MediaType): Int? = when {
        type == MediaType.MOVIE -> movieGenre
        movieGenre in shared -> movieGenre
        else -> movieToTv[movieGenre]
    }
}
