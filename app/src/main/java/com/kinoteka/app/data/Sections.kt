package com.kinoteka.app.data

import com.kinoteka.app.data.tmdb.TmdbItem
import com.kinoteka.app.i18n.tr
import java.time.LocalDate

enum class MediaType(val path: String, private val uk: String, private val en: String, private val ru: String) {
    MOVIE("movie", "Фільм", "Movie", "Фильм"),
    TV("tv", "Серіал", "TV show", "Сериал");

    val label: String get() = tr(uk, en, ru)

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

    /**
     * Жанры, которые разделы обычно исключают. Если пользователь выбрал такой жанр
     * явно (например «Мультфільм» в «Серіалах»), исключение снимается.
     */
    val overriding = setOf(ANIMATION, DOCUMENTARY, NEWS, REALITY, TALK)

    /**
     * Подборки для сериалов по ключевым словам TMDB: у сериалов нет жанров
     * «Жахи», «Мелодрама», «Трилер», «Фентезі», поэтому ищем по темам.
     */
    val tvKeywordGenres: List<Pair<String, String>>
        get() = listOf(
            tr("Жахи", "Horror", "Ужасы") to "315058|6152|3133|12377|162846|353801", // horror, supernatural, vampire, zombie, ghost, horror fantasy
            tr("Мелодрама", "Romance", "Мелодрама") to "9840|9673", // romance, love
            tr("Трилер", "Thriller", "Триллер") to "316362|12565|366584", // thriller, psychological thriller, mystery thriller
            tr("Фентезі", "Fantasy", "Фэнтези") to "293198|2343|177895", // fantasy, magic, dark fantasy
        )
}

/** Плашка на экране жанров: настоящий жанр TMDB, подборка по теме или «другое». */
data class GenreOption(val name: String, val params: Map<String, String>, val kind: Kind) {
    enum class Kind { GENRE, KEYWORD, EXTRA }
}

private val ASIAN_LANGUAGES = setOf("ko", "zh", "cn", "ja", "th")

/**
 * Разделы приложения. TMDB знает только «фильм» и «сериал», поэтому дорамы,
 * аниме и т.д. мы отбираем фильтрами по жанру и языку оригинала.
 */
enum class Section(
    private val uk: String,
    private val en: String,
    private val ru: String,
    val types: List<MediaType>,
    val isBase: Boolean = true,
) {
    MOVIES("Фільми", "Movies", "Фильмы", listOf(MediaType.MOVIE)),
    SERIES("Серіали", "TV Shows", "Сериалы", listOf(MediaType.TV)),
    DORAMAS("Дорами", "Dramas", "Дорамы", listOf(MediaType.TV)),
    ANIME("Аніме", "Anime", "Аниме", listOf(MediaType.TV, MediaType.MOVIE)),
    CARTOONS("Мультфільми", "Cartoons", "Мультфильмы", listOf(MediaType.MOVIE, MediaType.TV)),
    DOCS("Документальне", "Documentaries", "Документальное", listOf(MediaType.MOVIE, MediaType.TV)),

    // Дополнительные подборки: по умолчанию скрыты, включаются в настройках.
    // Их фильмы остаются и в основных разделах (турецкий сериал — и в «Серіалах»).
    TURKISH("Турецькі серіали", "Turkish TV shows", "Турецкие сериалы", listOf(MediaType.TV), isBase = false),
    UKRAINIAN("Українське", "Ukrainian", "Украинское", listOf(MediaType.MOVIE, MediaType.TV), isBase = false),
    INDIAN("Індійське кіно", "Indian cinema", "Индийское кино", listOf(MediaType.MOVIE), isBase = false),
    KOREAN_MOVIES("Корейське кіно", "Korean cinema", "Корейское кино", listOf(MediaType.MOVIE), isBase = false),
    REALITY("Реаліті-шоу", "Reality shows", "Реалити-шоу", listOf(MediaType.TV), isBase = false),
    CLASSIC("Класика", "Classics", "Классика", listOf(MediaType.MOVIE), isBase = false),
    SHORTS("Короткометражки", "Short films", "Короткометражки", listOf(MediaType.MOVIE), isBase = false);

    /** Название раздела на текущем языке (только для показа; идентификатор — [name]). */
    val title: String get() = tr(uk, en, ru)

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
        TURKISH -> mapOf("with_original_language" to "tr", "without_genres" to "16,99,10763,10764,10767")
        UKRAINIAN -> if (type == MediaType.TV) mapOf("with_original_language" to "uk", "without_genres" to "10763,10767")
        else mapOf("with_original_language" to "uk")
        INDIAN -> mapOf("with_original_language" to "hi|ta|te|ml|kn")
        KOREAN_MOVIES -> mapOf("with_original_language" to "ko", "without_genres" to "16")
        REALITY -> mapOf("with_genres" to "10764")
        CLASSIC -> mapOf("primary_release_date.lte" to "1989-12-31", "without_genres" to "99")
        SHORTS -> mapOf("with_runtime.gte" to "1", "with_runtime.lte" to "40")
    }

    /** Подходит ли фильм разделу (проверка на нашей стороне поверх фильтров TMDB). */
    fun accepts(item: TmdbItem, type: MediaType): Boolean =
        if (isBase) classify(item, type) == this else true

    /** Жанр, который всегда входит в раздел (для аниме, мультфильмов, документального, реалити). */
    val fixedGenre: Int?
        get() = when (this) {
            ANIME, CARTOONS -> TmdbGenres.ANIMATION
            DOCS -> TmdbGenres.DOCUMENTARY
            REALITY -> TmdbGenres.REALITY
            else -> null
        }

    /** Страны для дорам (фильтр на экране раздела). */
    val countries: List<Pair<String, String>>
        get() = if (this == DORAMAS) listOf(
            "KR" to tr("Корея", "Korea", "Корея"),
            "CN" to tr("Китай", "China", "Китай"),
            "JP" to tr("Японія", "Japan", "Япония"),
            "TH" to tr("Таїланд", "Thailand", "Таиланд"),
            "TW" to tr("Тайвань", "Taiwan", "Тайвань"),
        ) else emptyList()

    fun homeFeeds(today: LocalDate = LocalDate.now()): List<Feed> {
        // Минимум голосов отсекает малоизвестный мусор (старые шоу, спорт, радио).
        val minVotes = when (this) {
            MOVIES -> 50
            SERIES -> 50
            DORAMAS, ANIME -> 20
            CARTOONS, DOCS -> 15
            KOREAN_MOVIES, INDIAN, CLASSIC -> 30
            TURKISH, REALITY, SHORTS -> 10
            UKRAINIAN -> 5
        }
        val popular = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to minVotes.toString())
        // У новинок голосов ещё мало, а у будущих премьер их нет совсем.
        val fresh = mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "3")
        val upcoming = mapOf("sort_by" to "popularity.desc")
        return when (this) {
            MOVIES -> listOf(
                Feed(tr("Популярне", "Popular", "Популярное"), MediaType.MOVIE, popular),
                Feed(
                    tr("Зараз у кіно", "Now in theaters", "Сейчас в кино"), MediaType.MOVIE, popular + mapOf(
                        "primary_release_date.gte" to today.minusDays(45).toString(),
                        "primary_release_date.lte" to today.toString(),
                    )
                ),
                Feed(
                    tr("Скоро", "Coming soon", "Скоро"), MediaType.MOVIE, upcoming + mapOf(
                        "primary_release_date.gte" to today.plusDays(1).toString(),
                        "primary_release_date.lte" to today.plusDays(150).toString(),
                    )
                ),
                Feed(tr("Найкращі за весь час", "All-time best", "Лучшие за всё время"), MediaType.MOVIE, topRated(2000)),
            )

            SERIES, DORAMAS -> buildList {
                add(Feed(tr("Популярне", "Popular", "Популярное"), MediaType.TV, popular))
                add(
                    Feed(
                        tr("Зараз виходять", "Airing now", "Сейчас выходят"), MediaType.TV, popular + mapOf(
                            "air_date.gte" to today.minusDays(7).toString(),
                            "air_date.lte" to today.plusDays(7).toString(),
                        )
                    )
                )
                add(
                    Feed(
                        tr("Новинки", "New releases", "Новинки"), MediaType.TV, fresh + mapOf(
                            "first_air_date.gte" to today.minusDays(180).toString(),
                            "first_air_date.lte" to today.toString(),
                        )
                    )
                )
                countries.forEach { (code, name) ->
                    add(Feed(name, MediaType.TV, popular + mapOf("with_origin_country" to code)))
                }
                add(Feed(tr("Найкращі за весь час", "All-time best", "Лучшие за всё время"), MediaType.TV, topRated(if (this@Section == DORAMAS) 150 else 500)))
            }

            ANIME, CARTOONS, DOCS -> listOf(
                Feed(tr("Популярні серіали", "Popular TV shows", "Популярные сериалы"), MediaType.TV, popular),
                Feed(tr("Популярні фільми", "Popular movies", "Популярные фильмы"), MediaType.MOVIE, popular),
                Feed(
                    tr("Нові серіали", "New TV shows", "Новые сериалы"), MediaType.TV, fresh + mapOf(
                        "first_air_date.gte" to today.minusDays(180).toString(),
                        "first_air_date.lte" to today.toString(),
                    )
                ),
                Feed(
                    tr("Нові фільми", "New movies", "Новые фильмы"), MediaType.MOVIE, fresh + mapOf(
                        "primary_release_date.gte" to today.minusDays(240).toString(),
                        "primary_release_date.lte" to today.toString(),
                    )
                ),
                Feed(tr("Найкращі серіали", "Best TV shows", "Лучшие сериалы"), MediaType.TV, topRated(200)),
                Feed(tr("Найкращі фільми", "Best movies", "Лучшие фильмы"), MediaType.MOVIE, topRated(300)),
            )

            TURKISH, REALITY -> listOf(
                Feed(tr("Популярне", "Popular", "Популярное"), MediaType.TV, popular),
                Feed(
                    tr("Зараз виходять", "Airing now", "Сейчас выходят"), MediaType.TV, popular + mapOf(
                        "air_date.gte" to today.minusDays(7).toString(),
                        "air_date.lte" to today.plusDays(7).toString(),
                    )
                ),
                Feed(
                    tr("Новинки", "New releases", "Новинки"), MediaType.TV, fresh + mapOf(
                        "first_air_date.gte" to today.minusDays(240).toString(),
                        "first_air_date.lte" to today.toString(),
                    )
                ),
                Feed(tr("Найкращі за весь час", "All-time best", "Лучшие за всё время"), MediaType.TV, topRated(if (this@Section == TURKISH) 50 else 30)),
            )

            UKRAINIAN -> listOf(
                Feed(tr("Популярні фільми", "Popular movies", "Популярные фильмы"), MediaType.MOVIE, popular),
                Feed(tr("Популярні серіали", "Popular TV shows", "Популярные сериалы"), MediaType.TV, popular),
                Feed(
                    tr("Нові фільми", "New movies", "Новые фильмы"), MediaType.MOVIE, fresh + mapOf(
                        "primary_release_date.gte" to today.minusDays(365).toString(),
                        "primary_release_date.lte" to today.toString(),
                    )
                ),
                Feed(tr("Найкращі фільми", "Best movies", "Лучшие фильмы"), MediaType.MOVIE, topRated(20)),
                Feed(tr("Найкращі серіали", "Best TV shows", "Лучшие сериалы"), MediaType.TV, topRated(10)),
            )

            INDIAN, KOREAN_MOVIES -> buildList {
                add(Feed(tr("Популярне", "Popular", "Популярное"), MediaType.MOVIE, popular))
                add(
                    Feed(
                        tr("Нові фільми", "New movies", "Новые фильмы"), MediaType.MOVIE, fresh + mapOf(
                            "primary_release_date.gte" to today.minusDays(180).toString(),
                            "primary_release_date.lte" to today.toString(),
                        )
                    )
                )
                if (this@Section == INDIAN) add(Feed(tr("Боллівуд (гінді)", "Bollywood (Hindi)", "Болливуд (хинди)"), MediaType.MOVIE, popular + mapOf("with_original_language" to "hi")))
                add(Feed(tr("Найкращі за весь час", "All-time best", "Лучшие за всё время"), MediaType.MOVIE, topRated(200)))
            }

            CLASSIC -> listOf(
                Feed(tr("Найкращі класичні фільми", "Best classic movies", "Лучшие классические фильмы"), MediaType.MOVIE, topRated(1000)),
                Feed(tr("Популярна класика", "Popular classics", "Популярная классика"), MediaType.MOVIE, popular),
                Feed(tr("1980-ті", "1980s", "1980-е"), MediaType.MOVIE, decade(1980, minVotes)),
                Feed(tr("1970-ті", "1970s", "1970-е"), MediaType.MOVIE, decade(1970, minVotes)),
                Feed(tr("1960-ті", "1960s", "1960-е"), MediaType.MOVIE, decade(1960, minVotes)),
                Feed(tr("1950-ті і раніше", "1950s and earlier", "1950-е и раньше"), MediaType.MOVIE, mapOf(
                    "sort_by" to "vote_average.desc",
                    "vote_count.gte" to "200",
                    "primary_release_date.lte" to "1959-12-31",
                )),
            )

            SHORTS -> listOf(
                Feed(tr("Популярні", "Popular", "Популярные"), MediaType.MOVIE, popular),
                Feed(tr("Найкращі", "Best", "Лучшие"), MediaType.MOVIE, topRated(100)),
                Feed(tr("Анімаційні", "Animated", "Анимационные"), MediaType.MOVIE, popular + mapOf("with_genres" to "16")),
                Feed(
                    tr("Нові", "New", "Новые"), MediaType.MOVIE, fresh + mapOf(
                        "primary_release_date.gte" to today.minusDays(365).toString(),
                        "primary_release_date.lte" to today.toString(),
                    )
                ),
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

        private fun decade(start: Int, minVotes: Int) = mapOf(
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to (minVotes * 5).toString(),
            "primary_release_date.gte" to "$start-01-01",
            "primary_release_date.lte" to "${start + 9}-12-31",
        )

        private fun topRated(minVotes: Int) = mapOf(
            "sort_by" to "vote_average.desc",
            "vote_count.gte" to minVotes.toString(),
        )
    }
}

/** Одна лента на главной: заголовок + параметры запроса. */
data class Feed(val title: String, val type: MediaType, val params: Map<String, String>)

/** Варианты сортировки на экране жанра. */
enum class SortOption(private val uk: String, private val en: String, private val ru: String) {
    POPULAR("Популярні", "Popular", "Популярные"),
    RATING("За рейтингом", "By rating", "По рейтингу"),
    NEWEST("Нові", "Newest", "Новые");

    val label: String get() = tr(uk, en, ru)

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
