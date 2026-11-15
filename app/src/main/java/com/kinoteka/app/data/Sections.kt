package com.kinoteka.app.data

import com.kinoteka.app.data.tmdb.TmdbItem
import java.time.LocalDate

enum class MediaType(val path: String, val label: String) {
    MOVIE("movie", "Фільм"),
    TV("tv", "Серіал");

    companion object {
        fun fromPath(path: String?): MediaType? = entries.firstOrNull { it.path == path }
    }
}

/** Жанры TMDB, которые у нас являются отдельными разделами (а не жанрами). */
object TmdbGenres {
    const val ANIMATION = 16
    const val DOCUMENTARY = 99
    const val NEWS = 10763
    const val REALITY = 10764
    const val TALK = 10767

    /** Не показываем в списке жанров: это либо отдельный раздел, либо ТВ-шоу. */
    val hiddenInGenreList = setOf(ANIMATION, DOCUMENTARY, NEWS, REALITY, TALK)
}

private val ASIAN_LANGUAGES = setOf("ko", "zh", "cn", "ja", "th")

/**
 * Разделы приложения. TMDB знает только «фильм» и «сериал», поэтому дорамы,
 * аниме и т.д. мы отбираем фильтрами по жанру и языку оригинала.
 */
enum class Section(val title: String, val types: List<MediaType>) {
    MOVIES("Фільми", listOf(MediaType.MOVIE)),
    SERIES("Серіали", listOf(MediaType.TV)),
    DORAMAS("Дорами", listOf(MediaType.TV)),
    ANIME("Аніме", listOf(MediaType.TV, MediaType.MOVIE)),
    CARTOONS("Мультфільми", listOf(MediaType.MOVIE, MediaType.TV)),
    DOCS("Документальне", listOf(MediaType.MOVIE, MediaType.TV));

    /** Параметры запроса discover, которые сужают выдачу до раздела. */
    fun baseParams(type: MediaType): Map<String, String> = when (this) {
        MOVIES -> mapOf("without_genres" to "16,99")
        SERIES -> mapOf("without_genres" to "16,99,10763,10764,10767")
        DORAMAS -> mapOf(
            "with_original_language" to "ko|zh|cn|ja|th",
            "without_genres" to "16,99,10763,10764,10767",
        )
        ANIME -> mapOf("with_genres" to "16", "with_original_language" to "ja")
        CARTOONS -> mapOf("with_genres" to "16")
        DOCS -> mapOf("with_genres" to "99")
    }

    /** Жанр, который всегда входит в раздел (для аниме, мультфильмов, документального). */
    val fixedGenre: Int?
        get() = when (this) {
            ANIME, CARTOONS -> TmdbGenres.ANIMATION
            DOCS -> TmdbGenres.DOCUMENTARY
            else -> null
        }

    /** Страны для дорам (фильтр на экране раздела). */
    val countries: List<Pair<String, String>>
        get() = if (this == DORAMAS) listOf(
            "KR" to "Корея", "CN" to "Китай", "JP" to "Японія", "TH" to "Таїланд", "TW" to "Тайвань",
        ) else emptyList()

    fun homeFeeds(today: LocalDate = LocalDate.now()): List<Feed> {
        // Минимум голосов отсекает малоизвестный мусор (старые шоу, спорт, радио).
        val minVotes = when (this) {
            MOVIES -> 50
            SERIES -> 50
            DORAMAS, ANIME -> 20
            CARTOONS, DOCS -> 15
        }
        val popular = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to minVotes.toString())
        // У новинок голосов ещё мало, а у будущих премьер их нет совсем.
        val fresh = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "3")
        val upcoming = mapOf("sort_by" to "popularity.desc")
        return when (this) {
            MOVIES -> listOf(
                Feed("Популярне", MediaType.MOVIE, popular),
                Feed(
                    "Зараз у кіно", MediaType.MOVIE, popular + mapOf(
                        "primary_release_date.gte" to today.minusDays(45).toString(),
                        "primary_release_date.lte" to today.toString(),
                    )
                ),
                Feed(
                    "Скоро", MediaType.MOVIE, upcoming + mapOf(
                        "primary_release_date.gte" to today.plusDays(1).toString(),
                        "primary_release_date.lte" to today.plusDays(150).toString(),
                    )
                ),
                Feed("Найкращі за весь час", MediaType.MOVIE, topRated(2000)),
            )

            SERIES, DORAMAS -> buildList {
                add(Feed("Популярне", MediaType.TV, popular))
                add(
                    Feed(
                        "Зараз виходять", MediaType.TV, popular + mapOf(
                            "air_date.gte" to today.minusDays(7).toString(),
                            "air_date.lte" to today.plusDays(7).toString(),
                        )
                    )
                )
                add(
                    Feed(
                        "Новинки", MediaType.TV, fresh + mapOf(
                            "first_air_date.gte" to today.minusDays(180).toString(),
                            "first_air_date.lte" to today.toString(),
                        )
                    )
                )
                countries.forEach { (code, name) ->
                    add(Feed(name, MediaType.TV, popular + mapOf("with_origin_country" to code)))
                }
                add(Feed("Найкращі за весь час", MediaType.TV, topRated(if (this@Section == DORAMAS) 150 else 500)))
            }

            ANIME, CARTOONS, DOCS -> listOf(
                Feed("Популярні серіали", MediaType.TV, popular),
                Feed("Популярні фільми", MediaType.MOVIE, popular),
                Feed(
                    "Нові серіали", MediaType.TV, fresh + mapOf(
                        "first_air_date.gte" to today.minusDays(180).toString(),
                        "first_air_date.lte" to today.toString(),
                    )
                ),
                Feed(
                    "Нові фільми", MediaType.MOVIE, fresh + mapOf(
                        "primary_release_date.gte" to today.minusDays(240).toString(),
                        "primary_release_date.lte" to today.toString(),
                    )
                ),
                Feed("Найкращі серіали", MediaType.TV, topRated(200)),
                Feed("Найкращі фільми", MediaType.MOVIE, topRated(300)),
            )
        }
    }

    companion object {
        fun fromName(name: String?): Section? = entries.firstOrNull { it.name == name }

        /** К какому разделу относится конкретный фильм/сериал. */
        fun classify(type: MediaType, genreIds: Collection<Int>, language: String?): Section = when {
            TmdbGenres.ANIMATION in genreIds -> if (language == "ja") ANIME else CARTOONS
            TmdbGenres.DOCUMENTARY in genreIds -> DOCS
            type == MediaType.TV && language in ASIAN_LANGUAGES -> DORAMAS
            type == MediaType.TV -> SERIES
            else -> MOVIES
        }

        fun classify(item: TmdbItem, type: MediaType): Section =
            classify(type, item.genreIds, item.originalLanguage)

        private fun topRated(minVotes: Int) = mapOf(
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to minVotes.toString(),
        )
    }
}

/** Одна лента на главной: заголовок + параметры запроса. */
data class Feed(val title: String, val type: MediaType, val params: Map<String, String>)

/** Варианты сортировки на экране жанра. */
enum class SortOption(val label: String) {
    POPULAR("Популярні"),
    RATING("За рейтингом"),
    NEWEST("Нові");

    fun params(type: MediaType, today: LocalDate = LocalDate.now()): Map<String, String> = when (this) {
        POPULAR -> mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "15")
        RATING -> mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "150")
        NEWEST -> if (type == MediaType.MOVIE) mapOf(
            "sort_by" to "primary_release_date.desc",
            "primary_release_date.lte" to today.toString(),
            "vote_count.gte" to "10",
        ) else mapOf(
            "sort_by" to "first_air_date.desc",
            "first_air_date.lte" to today.toString(),
            "vote_count.gte" to "10",
        )
    }
}
