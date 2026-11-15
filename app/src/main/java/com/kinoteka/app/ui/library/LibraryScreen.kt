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
        TITLE -> list.sortedBy { it.title.lowercase() }
        YEAR -> list.sortedByDescending { it.year ?: "" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onItem: (LibraryEntry) -> Unit, onAccount: () -> Unit) {
    val all = LocalLibrary.current.values.toList()
    val syncState by LocalContainer.current.sync.state.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }
    val section = Section.fromName(sectionName)

    val bySection = all.filter { section == null || it.sectionEnum == section }
    val visible = sort.sort(bySection.filter(filter::matches))
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
                    title = { Text("Мої оцінки", fontWeight = FontWeight.Bold) },
                    actions = {
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
                    },
                )
                SectionChips(selected = section, onSelect = { sectionName = it?.name }, showAll = true, baseOnly = true)
                Spacer(Modifier.height(4.dp))
                ChipRow(LibraryFilter.entries, filter, { it.label }, { filter = it })
                Text(
                    stats,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
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
                        title = e.title,
                        posterPath = e.posterPath,
                        subtitle = listOfNotNull(if (e.favorite) "❤" else null, e.year, e.status?.label).joinToString(" · "),
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
