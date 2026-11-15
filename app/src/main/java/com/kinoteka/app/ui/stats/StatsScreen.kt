package com.kinoteka.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.details.SnakeRating
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// ---------- Змейка-питомец ----------

data class SnakeLevel(val name: String, val emoji: String, val from: Int)

val snakeLevels: List<SnakeLevel> get() = listOf(
    SnakeLevel(tr("Яйце", "Egg", "Яйцо"), "🥚", 0),
    SnakeLevel(tr("Малюк", "Baby", "Малыш"), "🐍", 1),
    SnakeLevel(tr("Вужик", "Grass snake", "Ужик"), "🐍", 5),
    SnakeLevel(tr("Полоз", "Rat snake", "Полоз"), "🐍", 15),
    SnakeLevel(tr("Пітон", "Python", "Питон"), "🐍", 30),
    SnakeLevel(tr("Удав", "Boa", "Удав"), "🐍", 60),
    SnakeLevel(tr("Анаконда", "Anaconda", "Анаконда"), "🐍", 100),
    SnakeLevel(tr("Королівська кобра", "King cobra", "Королевская кобра"), "👑", 200),
)

/** Очки змейки: каждый просмотренный фильм/сериал = 1, каждые 5 серий = ещё 1. */
fun snakePoints(entries: Collection<LibraryEntry>): Int =
    entries.count { it.isWatched } + entries.sumOf { it.watchedEpisodeCount } / 5

val LibraryEntry.isWatched: Boolean
    get() = status == WatchStatus.WATCHED || rating != null || (type == MediaType.TV && watchedEpisodeCount > 0)

// ---------- Достижения ----------

data class Achievement(val emoji: String, val title: String, val description: String, val progress: Int, val goal: Int) {
    val unlocked get() = progress >= goal
}

fun achievements(entries: Collection<LibraryEntry>, collections: Int): List<Achievement> {
    val watched = entries.filter { it.isWatched }
    fun section(s: Section) = watched.count { it.sectionEnum == s }
    return listOf(
        Achievement("⭐", tr("Перша оцінка", "First rating", "Первая оценка"), tr("Оцініть будь-який фільм", "Rate any movie", "Оцените любой фильм"), entries.count { it.rating != null }, 1),
        Achievement("🎬", tr("Кіноман", "Movie buff", "Киноман"), tr("Перегляньте 10 фільмів", "Watch 10 movies", "Посмотрите 10 фильмов"), watched.count { it.type == MediaType.MOVIE }, 10),
        Achievement("🍿", tr("Кіногурман", "Film gourmet", "Киногурман"), tr("Перегляньте 50 фільмів", "Watch 50 movies", "Посмотрите 50 фильмов"), watched.count { it.type == MediaType.MOVIE }, 50),
        Achievement("📺", tr("Серіаломан", "Binge-watcher", "Сериаломан"), tr("Перегляньте 5 серіалів", "Watch 5 TV shows", "Посмотрите 5 сериалов"), watched.count { it.type == MediaType.TV }, 5),
        Achievement("🎞", tr("Марафонець", "Marathoner", "Марафонец"), tr("Відмітьте 100 серій", "Check off 100 episodes", "Отметьте 100 серий"), entries.sumOf { it.watchedEpisodeCount }, 100),
        Achievement("🌸", tr("Дорамщик", "Drama fan", "Дорамщик"), tr("Перегляньте 10 дорам", "Watch 10 dramas", "Посмотрите 10 дорам"), section(Section.DORAMAS), 10),
        Achievement("✨", tr("Отаку", "Otaku", "Отаку"), tr("Перегляньте 10 аніме", "Watch 10 anime", "Посмотрите 10 аниме"), section(Section.ANIME), 10),
        Achievement("🧸", tr("Дитинство", "Childhood", "Детство"), tr("Перегляньте 10 мультфільмів", "Watch 10 cartoons", "Посмотрите 10 мультфильмов"), section(Section.CARTOONS), 10),
        Achievement("🖋", tr("Кінокритик", "Film critic", "Кинокритик"), tr("Напишіть 10 нотаток", "Write 10 notes", "Напишите 10 заметок"), entries.count { it.note.isNotBlank() }, 10),
        Achievement("❤", tr("Закоханий у кіно", "In love with movies", "Влюблён в кино"), tr("Позначте 10 улюблених", "Mark 10 favorites", "Добавьте 10 в избранное"), entries.count { it.favorite }, 10),
        Achievement("📁", tr("Колекціонер", "Collector", "Коллекционер"), tr("Створіть 3 підбірки", "Create 3 collections", "Создайте 3 подборки"), collections, 3),
        Achievement("🎨", tr("Свій стиль", "Own style", "Свой стиль"), tr("Поставте свій постер", "Set your own poster", "Поставьте свой постер"), entries.count { it.customPoster != null }, 1),
        Achievement("🏛", tr("Класика", "Classics", "Классика"), tr("Перегляньте 5 фільмів до 1980 року", "Watch 5 movies from before 1980", "Посмотрите 5 фильмов до 1980 года"), watched.count { (it.year?.toIntOrNull() ?: 9999) < 1980 }, 5),
        Achievement("💯", tr("Сотня", "Century", "Сотня"), tr("Поставте 100 оцінок", "Give 100 ratings", "Поставьте 100 оценок"), entries.count { it.rating != null }, 100),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val entries = LocalLibrary.current.values
    val collections = LocalContainer.current.library.collections.value.size
    val watched = entries.filter { it.isWatched }
    val movies = watched.filter { it.type == MediaType.MOVIE }
    val series = watched.filter { it.type == MediaType.TV }
    val episodes = entries.sumOf { it.watchedEpisodeCount }
    // Время: фильм ≈ его длительность (или 110 хв), серия ≈ её длительность (или 45 хв).
    val minutes = movies.sumOf { it.runtime ?: 110 } +
        entries.filter { it.type == MediaType.TV }.sumOf { it.watchedEpisodeCount * (it.runtime ?: 45) }
    val ratings = entries.mapNotNull { it.score }
    val points = snakePoints(entries)
    val levelIndex = snakeLevels.indexOfLast { points >= it.from }
    val level = snakeLevels[levelIndex]
    val next = snakeLevels.getOrNull(levelIndex + 1)
    val achievementList = achievements(entries, collections)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr("Статистика і змійка", "Stats and snake", "Статистика и змейка")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Змейка-питомец
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Ваша змійка", "Your snake", "Ваша змейка"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${level.emoji} ${level.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (levelIndex == 0) {
                            Text(
                                tr(
                                    "Подивіться і оцініть перший фільм — і з яйця вилупиться змійка!",
                                    "Watch and rate your first movie, and a snake will hatch from the egg!",
                                    "Посмотрите и оцените первый фильм — и из яйца вылупится змейка!",
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            // Чем выше уровень, тем длиннее змейка.
                            SnakeRating(rating = (levelIndex + 3).coerceAtMost(10).toDouble(), onRate = {}, onClear = {}, interactive = false)
                        }
                        Text(tr("Рівень ${levelIndex + 1} з ${snakeLevels.size}", "Level ${levelIndex + 1} of ${snakeLevels.size}", "Уровень ${levelIndex + 1} из ${snakeLevels.size}") + " · $points ${plural(points, "очко|очки|очок", "point|points", "очко|очка|очков")}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (next != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ((points - level.from).toFloat() / (next.from - level.from)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                            Text(
                                tr(
                                    "Ще ${next.from - points} до рівня «${next.name}»",
                                    "${next.from - points} more to level “${next.name}”",
                                    "Ещё ${next.from - points} до уровня «${next.name}»",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text(
                            tr(
                                "Змійка росте з кожним переглянутим фільмом і кожними 5 серіями.",
                                "The snake grows with every movie you watch and every 5 episodes.",
                                "Змейка растёт с каждым просмотренным фильмом и каждыми 5 сериями.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Цифры
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val hours = minutes / 60
                    val favorites = entries.count { it.favorite }
                    StatTile("${movies.size}", plural(movies.size, "фільм|фільми|фільмів", "movie|movies", "фильм|фильма|фильмов"))
                    StatTile("${series.size}", plural(series.size, "серіал|серіали|серіалів", "TV show|TV shows", "сериал|сериала|сериалов"))
                    StatTile("$episodes", plural(episodes, "серія|серії|серій", "episode|episodes", "серия|серии|серий"))
                    StatTile("≈$hours", plural(hours, "година|години|годин", "hour|hours", "час|часа|часов"))
                    StatTile(if (ratings.isEmpty()) "—" else String.format(Locale.US, "%.1f", ratings.average()), tr("середня оцінка", "average rating", "средняя оценка"))
                    StatTile("$favorites", plural(favorites, "улюблений|улюблених|улюблених", "favorite|favorites", "избранный|избранных|избранных"))
                }

                if (ratings.isNotEmpty()) ChartCard(tr("Мої оцінки", "My ratings", "Мои оценки")) {
                    val counts = (1..10).map { r -> ratings.count { Math.round(it).toInt() == r } }
                    BarChart(counts, (1..10).map { it.toString() })
                }

                // По месяцам (последние 12)
                val zone = ZoneId.systemDefault()
                val now = YearMonth.now()
                val months = (11 downTo 0).map { now.minusMonths(it.toLong()) }
                val byMonth = entries.mapNotNull { it.watchedAt }.groupingBy { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }.eachCount()
                if (byMonth.isNotEmpty()) ChartCard(tr("Переглянуто за місяцями", "Watched by month", "Просмотрено по месяцам")) {
                    BarChart(
                        months.map { byMonth[it] ?: 0 },
                        months.map { it.month.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).take(3) },
                    )
                }

                val genres = watched.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(7)
                if (genres.isNotEmpty()) ChartCard(tr("Улюблені жанри", "Favorite genres", "Любимые жанры")) {
                    val max = genres.first().value
                    genres.forEach { (name, count) -> HorizontalBar(name, count, max) }
                }

                val sections = watched.groupingBy { it.sectionEnum }.eachCount().entries.sortedByDescending { it.value }
                if (sections.isNotEmpty()) ChartCard(tr("За розділами", "By section", "По разделам")) {
                    val max = sections.first().value
                    sections.forEach { (s, count) -> HorizontalBar(s.title, count, max) }
                }

                // Достижения
                Text(
                    tr("Досягнення", "Achievements", "Достижения") + " · " +
                        tr("${achievementList.count { it.unlocked }} з ${achievementList.size}", "${achievementList.count { it.unlocked }} of ${achievementList.size}", "${achievementList.count { it.unlocked }} из ${achievementList.size}"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    achievementList.sortedByDescending { it.unlocked }.forEach { AchievementTile(it) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String) {
    Card(Modifier.width(104.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Вертикальные столбики с подписями снизу и числом сверху. */
@Composable
private fun BarChart(values: List<Int>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text("$v", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max * 0.75f).coerceAtLeast(if (v > 0) 0.03f else 0.005f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (v > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
                Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HorizontalBar(label: String, value: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(14.dp)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(value.toFloat() / max.coerceAtLeast(1))
                    .clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary),
            )
        }
        Text("  $value", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AchievementTile(a: Achievement) {
    Card(
        Modifier.width(150.dp).alpha(if (a.unlocked) 1f else 0.55f),
        colors = if (a.unlocked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(a.emoji, style = MaterialTheme.typography.headlineMedium)
            Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (a.unlocked) {
                Text(tr("✓ Отримано", "✓ Unlocked", "✓ Получено"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                LinearProgressIndicator(
                    progress = { a.progress.toFloat() / a.goal },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text("${a.progress.coerceAtMost(a.goal)} / ${a.goal}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
