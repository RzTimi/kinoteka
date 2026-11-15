package com.kinoteka.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.kinoteka.app.ui.stats.snakeLevels
import com.kinoteka.app.ui.stats.snakePoints
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinoteka.app.ui.LocalContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.ui.components.ChipRow
import com.kinoteka.app.ui.components.EmptyBox
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.PosterCard
import com.kinoteka.app.ui.components.SectionChips
import java.util.Locale

private enum class LibraryFilter(val label: String) {
    ALL("Усі"),
    FAVORITE("❤ Улюблене"),
    RATED("Оцінені"),
    WANT("Хочу"),
    WATCHING("Дивлюсь"),
    REWATCHING("Переглядаю"),
    WATCHED("Переглянуто"),
    ON_HOLD("Відкладено"),
    DROPPED("Кинув");

    fun matches(e: LibraryEntry) = when (this) {
        ALL -> true
        FAVORITE -> e.favorite
        RATED -> e.rating != null
        WANT -> e.status == WatchStatus.WANT
        WATCHING -> e.status == WatchStatus.WATCHING
        REWATCHING -> e.status == WatchStatus.REWATCHING
        WATCHED -> e.status == WatchStatus.WATCHED
        ON_HOLD -> e.status == WatchStatus.ON_HOLD
        DROPPED -> e.status == WatchStatus.DROPPED
    }
}

private enum class LibrarySort(val label: String) {
    RECENT("Спочатку нові"), RATING("За моєю оцінкою"), TITLE("За назвою"), YEAR("За роком");

    fun sort(list: List<LibraryEntry>): List<LibraryEntry> = when (this) {
        RECENT -> list.sortedByDescending { it.updatedAt }
        RATING -> list.sortedWith(compareByDescending<LibraryEntry> { it.rating ?: -1 }.thenByDescending { it.updatedAt })
        TITLE -> list.sortedBy { it.displayTitle.lowercase() }
        YEAR -> list.sortedByDescending { it.year ?: "" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onItem: (LibraryEntry) -> Unit,
    onAccount: () -> Unit,
    onStats: () -> Unit = {},
    onAddManual: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val all = LocalLibrary.current.values.toList()
    val container = LocalContainer.current
    val syncState by container.sync.state.collectAsStateWithLifecycle()
    val collections by container.library.collections.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val section = Section.fromName(sectionName)

    val bySection = all.filter { section == null || it.sectionEnum == section }
    val genresInLibrary = bySection.flatMap { it.genres }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }
    val visible = sort.sort(
        bySection.filter(filter::matches)
            .filter { collection == null || collection in it.collections }
            .filter { genre == null || genre in it.genres }
            .filter {
                query.isBlank() || it.displayTitle.contains(query.trim(), ignoreCase = true) ||
                    it.title.contains(query.trim(), ignoreCase = true) || it.note.contains(query.trim(), ignoreCase = true)
            }
    )
    val rated = bySection.mapNotNull { it.rating }
    val stats = buildString {
        append("Усього: ${bySection.size}")
        if (rated.isNotEmpty()) append(" · Оцінено: ${rated.size} · Середня: ${String.format(Locale.US, "%.1f", rated.average())}")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searching) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Назва або нотатка…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Default.Close, "Закрити пошук") }
                                },
                            )
                        } else {
                            Text("Мої оцінки", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Пошук у бібліотеці") }
                        // Облако: вход в Google / состояние синхронизации.
                        IconButton(onClick = onAccount) {
                            when {
                                syncState.syncing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                syncState.signedIn && syncState.error == null -> Icon(Icons.Default.CloudDone, "Синхронізовано з Google Диском")
                                syncState.signedIn -> Icon(Icons.Default.SyncProblem, "Помилка синхронізації", tint = MaterialTheme.colorScheme.error)
                                else -> Icon(Icons.Default.CloudOff, "Увійти через Google")
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Сортування") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                LibrarySort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(if (s == sort) "✓ ${s.label}" else s.label) },
                                        onClick = { sort = s; sortMenu = false },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Ще") }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Статистика і змійка") },
                                    leadingIcon = { Icon(Icons.Default.BarChart, null) },
                                    onClick = { moreMenu = false; onStats() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Додати фільм вручну") },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = { moreMenu = false; onAddManual() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Імпорт з Letterboxd / CSV") },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                                    onClick = { moreMenu = false; onImport() },
                                )
                            }
                        }
                    },
                )
                SectionChips(selected = section, onSelect = { sectionName = it?.name }, showAll = true, baseOnly = true)
                Spacer(Modifier.height(4.dp))
                ChipRow(LibraryFilter.entries, filter, { it.label }, { filter = it })
                if (collections.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val options: List<String?> = listOf<String?>(null) + collections.entries.sortedBy { it.value }.map { it.key }
                    ChipRow(options, collection, { it?.let { n -> "📁 $n" } ?: "Усі підбірки" }, { collection = it })
                }
                if (genresInLibrary.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    val options: List<String?> = listOf<String?>(null) + genresInLibrary
                    ChipRow(options, genre, { it ?: "Усі жанри" }, { genre = it })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Змейка-питомец: уровень, нажатие — статистика.
                    val points = snakePoints(all)
                    val level = snakeLevels.last { points >= it.from }
                    Text(
                        "${level.emoji} ${level.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onStats).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        if (visible.isEmpty()) {
            EmptyBox(
                if (all.isEmpty()) "Тут з'являться фільми, які ви оціните або додасте в «Хочу подивитись».\nВідкрийте будь-який фільм і поставте зірки ⭐"
                else "Нічого не знайдено за цим фільтром",
                modifier,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(visible, key = { it.key }) { e ->
                    PosterCard(
                        title = e.displayTitle,
                        posterPath = e.posterPath,
                        subtitle = listOfNotNull(
                            if (e.favorite) "❤" else null,
                            e.year,
                            e.status?.label,
                            e.watchedEpisodeCount.takeIf { it > 0 && e.type == com.kinoteka.app.data.MediaType.TV }?.let { "$it сер." },
                        ).joinToString(" · "),
                        tmdbRating = null,
                        myRating = e.rating,
                        onClick = { onItem(e) },
                        customPoster = e.customPoster,
                    )
                }
            }
        }
    }
}
