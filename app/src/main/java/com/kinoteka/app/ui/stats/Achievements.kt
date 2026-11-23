package com.kinoteka.app.ui.stats

import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// ---------- Змейка-питомец: уровни ----------

data class SnakeLevel(val name: String, val emoji: String, val from: Int)

val snakeLevels: List<SnakeLevel> get() = listOf(
    SnakeLevel(tr("Яйце", "Egg", "Яйцо"), "🥚", 0),
    SnakeLevel(tr("Малюк", "Baby", "Малыш"), "🐣", 1),
    SnakeLevel(tr("Вужик", "Grass snake", "Ужик"), "🐍", 8),
    SnakeLevel(tr("Полоз", "Rat snake", "Полоз"), "🐍", 20),
    SnakeLevel(tr("Гадюка", "Viper", "Гадюка"), "🐍", 40),
    SnakeLevel(tr("Пітон", "Python", "Питон"), "🐍", 70),
    SnakeLevel(tr("Удав", "Boa", "Удав"), "🐍", 110),
    SnakeLevel(tr("Мамба", "Mamba", "Мамба"), "🐍", 160),
    SnakeLevel(tr("Анаконда", "Anaconda", "Анаконда"), "🐍", 220),
    SnakeLevel(tr("Тайпан", "Taipan", "Тайпан"), "🐍", 300),
    SnakeLevel(tr("Королівська кобра", "King cobra", "Королевская кобра"), "👑", 400),
    SnakeLevel(tr("Мудрий змій", "Wise serpent", "Мудрый змей"), "🧙", 520),
    SnakeLevel(tr("Уроборос", "Ouroboros", "Уроборос"), "♾️", 680),
    SnakeLevel(tr("Дракон", "Dragon", "Дракон"), "🐉", 880),
    SnakeLevel(tr("Легенда кіно", "Movie legend", "Легенда кино"), "🌟", 1150),
)

val LibraryEntry.isWatched: Boolean
    get() = status == WatchStatus.WATCHED || rating != null || (type == MediaType.TV && watchedEpisodeCount > 0)

/** Из чего складываются очки змейки. */
data class SnakePoints(val titles: Int, val episodes: Int, val achievements: Int) {
    val total: Int get() = titles + episodes + achievements
}

/**
 * Очки змейки: просмотренный фильм/сериал = 1, каждые 5 серий = 1,
 * плюс очки за достижения (🥉 3, 🥈 6, 🥇 12, 💎 25).
 */
fun snakePointsDetailed(entries: Collection<LibraryEntry>, collections: Int): SnakePoints = SnakePoints(
    titles = entries.count { it.isWatched },
    episodes = entries.sumOf { it.watchedEpisodeCount } / 5,
    achievements = achievements(entries, collections).filter { it.unlocked }.sumOf { it.tier.points },
)

fun snakePoints(entries: Collection<LibraryEntry>, collections: Int): Int = snakePointsDetailed(entries, collections).total

fun snakeLevelIndex(points: Int): Int = snakeLevels.indexOfLast { points >= it.from }.coerceAtLeast(0)

// ---------- Достижения ----------

enum class Tier(val medal: String, val points: Int) {
    BRONZE("🥉", 3), SILVER("🥈", 6), GOLD("🥇", 12), DIAMOND("💎", 25),
}

enum class AchievementCategory(private val uk: String, private val en: String, private val ru: String) {
    WATCHING("Перегляд", "Watching", "Просмотр"),
    GENRES("Жанри й розділи", "Genres & sections", "Жанры и разделы"),
    TIME("Епохи й час", "Eras & time", "Эпохи и время"),
    RATINGS("Оцінки й нотатки", "Ratings & notes", "Оценки и заметки"),
    COLLECTION("Колекція", "Collection", "Коллекция");

    val label: String get() = tr(uk, en, ru)
}

data class Achievement(
    val id: String,
    val category: AchievementCategory,
    val tier: Tier,
    val emoji: String,
    val title: String,
    val description: String,
    val progress: Int,
    val goal: Int,
) {
    val unlocked get() = progress >= goal
}

/** Ключевые слова жанров на трёх языках (жанры в библиотеке хранятся названиями). */
private val HORROR = listOf("жах", "ужас", "horror")
private val COMEDY = listOf("комед", "comedy")
private val ROMANCE = listOf("мелодрам", "romance")
private val SCIFI = listOf("фантаст", "фентез", "фэнтез", "fantasy", "sci-fi", "science fiction", "нф ")
private val CRIME = listOf("кримінал", "криминал", "crime", "трилер", "триллер", "thriller", "детектив", "mystery")
private val ACTION = listOf("бойовик", "боевик", "action")

private fun LibraryEntry.hasGenre(keys: List<String>): Boolean =
    genres.any { g -> val l = "${g.lowercase()} "; keys.any { l.contains(it) } }

fun achievements(entries: Collection<LibraryEntry>, collections: Int, today: LocalDate = LocalDate.now()): List<Achievement> {
    val zone = ZoneId.systemDefault()
    val watched = entries.filter { it.isWatched }
    val movies = watched.filter { it.type == MediaType.MOVIE }
    val shows = watched.filter { it.type == MediaType.TV }
    val episodes = entries.sumOf { it.watchedEpisodeCount }
    val scores = entries.mapNotNull { it.score }
    fun section(s: Section) = watched.count { it.sectionEnum == s }
    fun genre(keys: List<String>) = watched.count { it.hasGenre(keys) }
    val years = watched.mapNotNull { it.year?.toIntOrNull() }
    fun decade(start: Int) = years.count { it in start until start + 10 }
    val watchTimes = entries.mapNotNull { it.watchedAt }.map { Instant.ofEpochMilli(it).atZone(zone) }
    val months = watchTimes.map { YearMonth.from(it) }
    val bestMonth = months.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
    val streak = longestMonthStreak(months.toSet())
    val nightOwl = watchTimes.count { it.hour < 5 }

    val list = mutableListOf<Achievement>()
    fun add(id: String, cat: AchievementCategory, tier: Tier, emoji: String, title: String, desc: String, progress: Int, goal: Int) {
        list += Achievement(id, cat, tier, emoji, title, desc, progress, goal)
    }
    /** Серия ступеней одного достижения: I, II, III… */
    fun tiers(
        id: String, cat: AchievementCategory, emoji: String, title: String,
        desc: (Int) -> String, progress: Int, vararg steps: Pair<Int, Tier>,
    ) {
        steps.forEachIndexed { i, (goal, tier) ->
            val roman = listOf("I", "II", "III", "IV")[i]
            add("$id$i", cat, tier, emoji, if (steps.size > 1) "$title $roman" else title, desc(goal), progress, goal)
        }
    }
    val W = AchievementCategory.WATCHING
    val G = AchievementCategory.GENRES
    val T = AchievementCategory.TIME
    val R = AchievementCategory.RATINGS
    val C = AchievementCategory.COLLECTION
    val B = Tier.BRONZE
    val S = Tier.SILVER
    val Au = Tier.GOLD
    val D = Tier.DIAMOND

    // ---- Просмотр ----
    add("first_watch", W, B, "🎬", tr("Перший перегляд", "First watch", "Первый просмотр"),
        tr("Позначте перший фільм переглянутим", "Mark your first title as watched", "Отметьте первый фильм просмотренным"), watched.size, 1)
    tiers("movies", W, "🍿", tr("Кіноман", "Movie buff", "Киноман"),
        { tr("Перегляньте $it фільмів", "Watch $it movies", "Посмотрите $it фильмов") }, movies.size, 10 to B, 50 to S, 100 to Au, 250 to D)
    tiers("shows", W, "📺", tr("Серіаломан", "Binge-watcher", "Сериаломан"),
        { tr("Перегляньте $it серіалів", "Watch $it TV shows", "Посмотрите $it сериалов") }, shows.size, 5 to B, 20 to S, 50 to Au)
    tiers("episodes", W, "🎞", tr("Марафонець", "Marathoner", "Марафонец"),
        { tr("Відмітьте $it серій", "Check off $it episodes", "Отметьте $it серий") }, episodes, 50 to B, 250 to S, 1000 to Au, 3000 to D)
    tiers("finished", W, "✅", tr("До фіналу", "To the finale", "До финала"),
        { tr("Додивіться $it серіалів до кінця", "Finish $it TV shows", "Досмотрите $it сериалов до конца") },
        entries.count { it.type == MediaType.TV && it.status == WatchStatus.WATCHED }, 3 to B, 15 to S)
    add("rewatch", W, B, "🔁", tr("Знову й знову", "Again and again", "Снова и снова"),
        tr("Переглядайте 3 фільми повторно", "Rewatch 3 titles", "Пересматривайте 3 фильма"), entries.count { it.status == WatchStatus.REWATCHING }, 3)
    tiers("month_max", W, "🏃", tr("Кіномісяць", "Movie month", "Киномесяц"),
        { tr("Перегляньте $it за один місяць", "Watch $it in one month", "Посмотрите $it за один месяц") }, bestMonth, 10 to S, 20 to Au)
    add("night_owl", W, S, "🦉", tr("Нічний глядач", "Night owl", "Ночной зритель"),
        tr("Позначте 5 переглядів між північчю і 5 ранку", "Mark 5 titles watched between midnight and 5 am", "Отметьте 5 просмотров между полуночью и 5 утра"), nightOwl, 5)

    // ---- Жанры и разделы ----
    tiers("dramas", G, "🌸", tr("Дорамщик", "Drama fan", "Дорамщик"),
        { tr("Перегляньте $it дорам", "Watch $it dramas", "Посмотрите $it дорам") }, section(Section.DORAMAS), 5 to B, 20 to S, 50 to Au)
    tiers("anime", G, "✨", tr("Отаку", "Otaku", "Отаку"),
        { tr("Перегляньте $it аніме", "Watch $it anime", "Посмотрите $it аниме") }, section(Section.ANIME), 5 to B, 20 to S, 50 to Au)
    tiers("cartoons", G, "🧸", tr("Дитинство", "Childhood", "Детство"),
        { tr("Перегляньте $it мультфільмів", "Watch $it cartoons", "Посмотрите $it мультфильмов") }, section(Section.CARTOONS), 5 to B, 20 to S)
    tiers("docs", G, "🌍", tr("Документаліст", "Documentarian", "Документалист"),
        { tr("Перегляньте $it документальних", "Watch $it documentaries", "Посмотрите $it документальных") }, section(Section.DOCS), 3 to B, 15 to S)
    tiers("horror", G, "👻", tr("Не страшно", "Not scared", "Не страшно"),
        { tr("Перегляньте $it жахів", "Watch $it horror titles", "Посмотрите $it ужасов") }, genre(HORROR), 5 to B, 20 to S)
    tiers("comedy", G, "😂", tr("Реготун", "Laugher", "Хохотун"),
        { tr("Перегляньте $it комедій", "Watch $it comedies", "Посмотрите $it комедий") }, genre(COMEDY), 10 to B, 40 to S)
    tiers("romance", G, "💘", tr("Романтик", "Romantic", "Романтик"),
        { tr("Перегляньте $it мелодрам", "Watch $it romances", "Посмотрите $it мелодрам") }, genre(ROMANCE), 10 to B, 30 to S)
    tiers("scifi", G, "🚀", tr("Космонавт", "Astronaut", "Космонавт"),
        { tr("Перегляньте $it фантастики чи фентезі", "Watch $it sci-fi or fantasy titles", "Посмотрите $it фантастики или фэнтези") }, genre(SCIFI), 10 to B, 30 to S)
    tiers("crime", G, "🔍", tr("Детектив", "Detective", "Детектив"),
        { tr("Перегляньте $it детективів чи трилерів", "Watch $it crime or thriller titles", "Посмотрите $it детективов или триллеров") }, genre(CRIME), 10 to B, 30 to S)
    add("action", G, B, "💥", tr("Адреналін", "Adrenaline", "Адреналин"),
        tr("Перегляньте 10 бойовиків", "Watch 10 action titles", "Посмотрите 10 боевиков"), genre(ACTION), 10)
    tiers("genres", G, "🎨", tr("Всеїдний", "Omnivore", "Всеядный"),
        { tr("Перегляньте фільми $it різних жанрів", "Watch titles of $it different genres", "Посмотрите фильмы $it разных жанров") },
        watched.flatMap { e -> e.genres.map { it.lowercase() } }.distinct().size, 8 to B, 15 to Au)

    // ---- Эпохи и время ----
    tiers("classic", T, "🏛", tr("Класика", "Classics", "Классика"),
        { tr("Перегляньте $it фільмів до 1980 року", "Watch $it titles from before 1980", "Посмотрите $it фильмов до 1980 года") }, years.count { it < 1980 }, 5 to B, 20 to Au)
    add("eighties", T, B, "📼", tr("Ретро 80-х", "80s retro", "Ретро 80-х"), tr("Перегляньте 5 фільмів 80-х", "Watch 5 titles from the 80s", "Посмотрите 5 фильмов 80-х"), decade(1980), 5)
    add("nineties", T, B, "💿", tr("Ностальгія 90-х", "90s nostalgia", "Ностальгия 90-х"), tr("Перегляньте 10 фільмів 90-х", "Watch 10 titles from the 90s", "Посмотрите 10 фильмов 90-х"), decade(1990), 10)
    add("noughties", T, B, "📀", tr("Нульові", "The 2000s", "Нулевые"), tr("Перегляньте 10 фільмів 2000-х", "Watch 10 titles from the 2000s", "Посмотрите 10 фильмов 2000-х"), decade(2000), 10)
    tiers("decades", T, "🕰", tr("Мандрівник у часі", "Time traveller", "Путешественник во времени"),
        { tr("Фільми з $it різних десятиліть", "Titles from $it different decades", "Фильмы из $it разных десятилетий") }, years.map { it / 10 }.distinct().size, 5 to S, 8 to Au)
    add("fresh", T, B, "🆕", tr("Свіженьке", "Fresh", "Свеженькое"),
        tr("Перегляньте 5 новинок ${today.year} року", "Watch 5 titles from ${today.year}", "Посмотрите 5 новинок ${today.year} года"), years.count { it == today.year }, 5)
    tiers("streak", T, "📅", tr("Щомісяця", "Every month", "Каждый месяц"),
        { tr("Дивіться щось $it місяців поспіль", "Watch something $it months in a row", "Смотрите что-то $it месяцев подряд") }, streak, 3 to B, 6 to S, 12 to Au)
    add("epic", T, S, "⏳", tr("Епопея", "Epic", "Эпопея"),
        tr("Перегляньте фільм довше 3 годин", "Watch a movie longer than 3 hours", "Посмотрите фильм длиннее 3 часов"), movies.count { (it.runtime ?: 0) >= 180 }, 1)
    add("short", T, B, "⚡", tr("Коротко і ясно", "Short and sweet", "Коротко и ясно"),
        tr("Перегляньте 10 фільмів до 1,5 години", "Watch 10 movies under 1.5 hours", "Посмотрите 10 фильмов до 1,5 часа"), movies.count { (it.runtime ?: 0) in 1..90 }, 10)

    // ---- Оценки и заметки ----
    add("first_rating", R, B, "⭐", tr("Перша оцінка", "First rating", "Первая оценка"),
        tr("Оцініть будь-який фільм", "Rate any title", "Оцените любой фильм"), scores.size, 1)
    tiers("ratings", R, "🌟", tr("Цінитель", "Connoisseur", "Ценитель"),
        { tr("Поставте $it оцінок", "Give $it ratings", "Поставьте $it оценок") }, scores.size, 25 to B, 100 to S, 300 to Au)
    add("masterpiece", R, S, "💯", tr("Шедеври", "Masterpieces", "Шедевры"), tr("Поставте 10 п'ятьом фільмам", "Rate 5 titles 10", "Поставьте 10 пяти фильмам"), scores.count { it >= 10.0 }, 5)
    add("strict", R, B, "🍅", tr("Суворий критик", "Strict critic", "Строгий критик"), tr("Поставте 3 або менше п'ятьом фільмам", "Rate 5 titles 3 or lower", "Поставьте 3 или меньше пяти фильмам"), scores.count { it <= 3.0 }, 5)
    add("precise", R, B, "🎯", tr("Точність", "Precision", "Точность"), tr("Поставте 10 оцінок з десятими (5.2)", "Give 10 ratings with tenths (5.2)", "Поставьте 10 оценок с десятыми (5.2)"), scores.count { it % 1.0 != 0.0 }, 10)
    add("spectrum", R, Au, "🌈", tr("Увесь спектр", "Full spectrum", "Весь спектр"), tr("Використайте всі оцінки від 1 до 10", "Use every rating from 1 to 10", "Используйте все оценки от 1 до 10"),
        scores.map { Math.round(it).toInt() }.distinct().size, 10)
    tiers("notes", R, "🖋", tr("Кінокритик", "Film critic", "Кинокритик"),
        { tr("Напишіть $it нотаток", "Write $it notes", "Напишите $it заметок") }, entries.count { it.note.isNotBlank() }, 1 to B, 10 to S, 50 to Au)
    add("essay", R, S, "📜", tr("Літописець", "Chronicler", "Летописец"), tr("Напишіть нотатку довше 200 символів", "Write a note longer than 200 characters", "Напишите заметку длиннее 200 символов"),
        if (entries.any { it.note.length >= 200 }) 1 else 0, 1)

    // ---- Коллекция ----
    tiers("favorites", C, "❤", tr("Закоханий у кіно", "In love with movies", "Влюблён в кино"),
        { tr("Позначте $it улюблених", "Mark $it favorites", "Добавьте $it в избранное") }, entries.count { it.favorite }, 1 to B, 10 to S, 30 to Au)
    tiers("collections", C, "📁", tr("Колекціонер", "Collector", "Коллекционер"),
        { tr("Створіть $it підбірок", "Create $it collections", "Создайте $it подборок") }, collections, 1 to B, 5 to S)
    tiers("posters", C, "🎨", tr("Свій стиль", "Own style", "Свой стиль"),
        { tr("Поставте $it своїх постерів", "Set $it custom posters", "Поставьте $it своих постеров") }, entries.count { it.customPoster != null }, 1 to B, 10 to S)
    add("backdrop", C, B, "🖼", tr("Декоратор", "Decorator", "Декоратор"), tr("Поставте свій фон картки", "Set a custom background", "Поставьте свой фон карточки"), entries.count { it.customBackdrop != null }, 1)
    add("own_title", C, B, "✏️", tr("Своя назва", "Own title", "Своё название"), tr("Перейменуйте фільм", "Rename a title", "Переименуйте фильм"), entries.count { it.customTitle != null }, 1)
    tiers("want", C, "📌", tr("Великі плани", "Big plans", "Большие планы"),
        { tr("Додайте $it у «Хочу подивитись»", "Add $it to “Want to watch”", "Добавьте $it в «Хочу посмотреть»") }, entries.count { it.status == WatchStatus.WANT }, 10 to B, 50 to S)
    add("manual", C, B, "✍️", tr("Ручна робота", "Handmade", "Ручная работа"), tr("Додайте фільм вручну", "Add a title manually", "Добавьте фильм вручную"), entries.count { it.manual }, 1)
    add("dropped", C, B, "🙅", tr("Не моє", "Not my thing", "Не моё"), tr("Киньте 3 фільми чи серіали", "Drop 3 titles", "Бросьте 3 фильма или сериала"), entries.count { it.status == WatchStatus.DROPPED }, 3)
    add("on_hold", C, B, "⏸", tr("На потім", "For later", "На потом"), tr("Відкладіть 3 серіали", "Put 3 shows on hold", "Отложите 3 сериала"), entries.count { it.status == WatchStatus.ON_HOLD }, 3)
    add("library", C, Au, "📚", tr("Велика бібліотека", "Big library", "Большая библиотека"), tr("Зберіть 200 записів у бібліотеці", "Collect 200 titles in your library", "Соберите 200 записей в библиотеке"), entries.size, 200)
    return list
}

/** Самая длинная серия месяцев подряд, в каждый из которых что-то отмечено просмотренным. */
fun longestMonthStreak(months: Set<YearMonth>): Int {
    var best = 0
    for (m in months) {
        if (m.minusMonths(1) in months) continue // считаем только от начала серии
        var len = 1
        var cur = m.plusMonths(1)
        while (cur in months) { len++; cur = cur.plusMonths(1) }
        best = maxOf(best, len)
    }
    return best
}

// ---------- Гардероб змейки ----------

/** Как открывается вещь: уровнем змейки или достижением. */
sealed interface Unlock {
    data object Always : Unlock
    data class Level(val index: Int) : Unlock
    data class AchievementId(val id: String) : Unlock
    data class AchievementCount(val count: Int) : Unlock
}

enum class SnakeSkin(private val uk: String, private val en: String, private val ru: String, val unlock: Unlock) {
    GREEN("Зелена", "Green", "Зелёная", Unlock.Always),
    CORAL("Коралова", "Coral", "Коралловая", Unlock.AchievementId("notes1")),
    OCEAN("Океан", "Ocean", "Океан", Unlock.AchievementId("shows0")),
    SAKURA("Сакура", "Sakura", "Сакура", Unlock.AchievementId("dramas0")),
    VIOLET("Фіалкова", "Violet", "Фиолетовая", Unlock.AchievementId("anime0")),
    ICE("Крижана", "Ice", "Ледяная", Unlock.AchievementId("ratings0")),
    NIGHT("Нічна", "Night", "Ночная", Unlock.AchievementId("horror0")),
    GOLD("Золота", "Golden", "Золотая", Unlock.Level(8)),
    RAINBOW("Веселка", "Rainbow", "Радужная", Unlock.AchievementCount(30));

    val label: String get() = tr(uk, en, ru)
}

enum class SnakeHat(val emoji: String, private val uk: String, private val en: String, private val ru: String, val unlock: Unlock) {
    NONE("", "Без нічого", "Nothing", "Без ничего", Unlock.Always),
    BOW("🎀", "Бантик", "Bow", "Бантик", Unlock.AchievementId("favorites0")),
    CAP("🧢", "Кепка", "Cap", "Кепка", Unlock.AchievementId("movies0")),
    FLOWER("🌼", "Квіточка", "Flower", "Цветочек", Unlock.AchievementId("collections0")),
    GLASSES("🕶️", "Окуляри", "Sunglasses", "Очки", Unlock.AchievementId("favorites1")),
    POPCORN("🍿", "Попкорн", "Popcorn", "Попкорн", Unlock.AchievementId("movies1")),
    TOP_HAT("🎩", "Циліндр", "Top hat", "Цилиндр", Unlock.AchievementId("classic0")),
    HELMET("⛑️", "Каска", "Helmet", "Каска", Unlock.AchievementId("episodes1")),
    GRAD("🎓", "Шапочка магістра", "Graduation cap", "Шапочка магистра", Unlock.AchievementId("ratings1")),
    PARTY("🎉", "Свято", "Party", "Праздник", Unlock.AchievementCount(15)),
    CROWN("👑", "Корона", "Crown", "Корона", Unlock.Level(10));

    val label: String get() = tr(uk, en, ru)
}

/** Что сейчас открыто: уровень змейки и полученные достижения. */
data class SnakeProgress(val levelIndex: Int, val unlockedIds: Set<String>) {
    fun isOpen(u: Unlock): Boolean = when (u) {
        Unlock.Always -> true
        is Unlock.Level -> levelIndex >= u.index
        is Unlock.AchievementId -> u.id in unlockedIds
        is Unlock.AchievementCount -> unlockedIds.size >= u.count
    }

    /** Подсказка, как открыть. */
    fun hint(u: Unlock, all: List<Achievement>): String = when (u) {
        Unlock.Always -> ""
        is Unlock.Level -> tr("Рівень «${snakeLevels[u.index].name}»", "Level “${snakeLevels[u.index].name}”", "Уровень «${snakeLevels[u.index].name}»")
        is Unlock.AchievementId -> all.firstOrNull { it.id == u.id }?.let { "${it.emoji} ${it.title}: ${it.description}" }.orEmpty()
        is Unlock.AchievementCount -> tr("Отримайте ${u.count} досягнень", "Unlock ${u.count} achievements", "Получите ${u.count} достижений")
    }
}

fun snakeProgress(entries: Collection<LibraryEntry>, collections: Int): SnakeProgress {
    val list = achievements(entries, collections)
    val points = snakePointsDetailed(entries, collections).total
    return SnakeProgress(snakeLevelIndex(points), list.filter { it.unlocked }.mapTo(HashSet()) { it.id })
}
