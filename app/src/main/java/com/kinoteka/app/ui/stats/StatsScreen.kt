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

val snakeLevels = listOf(
    SnakeLevel("Яйце", "🥚", 0),
    SnakeLevel("Малюк", "🐍", 1),
    SnakeLevel("Вужик", "🐍", 5),
    SnakeLevel("Полоз", "🐍", 15),
    SnakeLevel("Пітон", "🐍", 30),
    SnakeLevel("Удав", "🐍", 60),
    SnakeLevel("Анаконда", "🐍", 100),
    SnakeLevel("Королівська кобра", "👑", 200),
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
        Achievement("⭐", "Перша оцінка", "Оцініть будь-який фільм", entries.count { it.rating != null }, 1),
        Achievement("🎬", "Кіноман", "Перегляньте 10 фільмів", watched.count { it.type == MediaType.MOVIE }, 10),
        Achievement("🍿", "Кіногурман", "Перегляньте 50 фільмів", watched.count { it.type == MediaType.MOVIE }, 50),
        Achievement("📺", "Серіаломан", "Перегляньте 5 серіалів", watched.count { it.type == MediaType.TV }, 5),
        Achievement("🎞", "Марафонець", "Відмітьте 100 серій", entries.sumOf { it.watchedEpisodeCount }, 100),
        Achievement("🌸", "Дорамщик", "Перегляньте 10 дорам", section(Section.DORAMAS), 10),
        Achievement("✨", "Отаку", "Перегляньте 10 аніме", section(Section.ANIME), 10),
        Achievement("🧸", "Дитинство", "Перегляньте 10 мультфільмів", section(Section.CARTOONS), 10),
        Achievement("🖋", "Кінокритик", "Напишіть 10 нотаток", entries.count { it.note.isNotBlank() }, 10),
        Achievement("❤", "Закоханий у кіно", "Позначте 10 улюблених", entries.count { it.favorite }, 10),
        Achievement("📁", "Колекціонер", "Створіть 3 підбірки", collections, 3),
        Achievement("🎨", "Свій стиль", "Поставте свій постер", entries.count { it.customPoster != null }, 1),
        Achievement("🏛", "Класика", "Перегляньте 5 фільмів до 1980 року", watched.count { (it.year?.toIntOrNull() ?: 9999) < 1980 }, 5),
        Achievement("💯", "Сотня", "Поставте 100 оцінок", entries.count { it.rating != null }, 100),
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
                title = { Text("Статистика і змійка") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
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
                        Text("Ваша змійка", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${level.emoji} ${level.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (levelIndex == 0) {
                            Text(
                                "Подивіться і оцініть перший фільм — і з яйця вилупиться змійка!",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            // Чем выше уровень, тем длиннее змейка.
                            SnakeRating(rating = (levelIndex + 3).coerceAtMost(10).toDouble(), onRate = {}, onClear = {}, interactive = false)
                        }
                        Text("Рівень ${levelIndex + 1} з ${snakeLevels.size} · $points очок", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (next != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ((points - level.from).toFloat() / (next.from - level.from)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                            Text(
                                "Ще ${next.from - points} до рівня «${next.name}»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text(
                            "Змійка росте з кожним переглянутим фільмом і кожними 5 серіями.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Цифры
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${movies.size}", "фільмів")
                    StatTile("${series.size}", "серіалів")
                    StatTile("$episodes", "серій")
                    StatTile("≈${minutes / 60}", "годин")
                    StatTile(if (ratings.isEmpty()) "—" else String.format(Locale.US, "%.1f", ratings.average()), "середня оцінка")
                    StatTile("${entries.count { it.favorite }}", "улюблених")
                }

                if (ratings.isNotEmpty()) ChartCard("Мої оцінки") {
                    val counts = (1..10).map { r -> ratings.count { Math.round(it).toInt() == r } }
                    BarChart(counts, (1..10).map { it.toString() })
                }

                // По месяцам (последние 12)
                val zone = ZoneId.systemDefault()
                val now = YearMonth.now()
                val months = (11 downTo 0).map { now.minusMonths(it.toLong()) }
                val byMonth = entries.mapNotNull { it.watchedAt }.groupingBy { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }.eachCount()
                if (byMonth.isNotEmpty()) ChartCard("Переглянуто за місяцями") {
                    BarChart(
                        months.map { byMonth[it] ?: 0 },
                        months.map { it.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.forLanguageTag("uk")).take(3) },
                    )
                }

                val genres = watched.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(7)
                if (genres.isNotEmpty()) ChartCard("Улюблені жанри") {
                    val max = genres.first().value
                    genres.forEach { (name, count) -> HorizontalBar(name, count, max) }
                }

                val sections = watched.groupingBy { it.sectionEnum }.eachCount().entries.sortedByDescending { it.value }
                if (sections.isNotEmpty()) ChartCard("За розділами") {
                    val max = sections.first().value
                    sections.forEach { (s, count) -> HorizontalBar(s.title, count, max) }
                }

                // Достижения
                Text(
                    "Досягнення · ${achievementList.count { it.unlocked }} з ${achievementList.size}",
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
                Text("✓ Отримано", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
