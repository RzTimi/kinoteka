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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
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
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.share.ListShareDialog
import java.util.Locale

private enum class LibraryFilter(private val uk: String, private val en: String, private val ru: String) {
    ALL("Усі", "All", "Все"),
    FAVORITE("❤ Улюблене", "❤ Favorite", "❤ Избранное"),
    RATED("Оцінені", "Rated", "Оценённые"),
    WANT("Хочу", "Want", "Хочу"),
    WATCHING("Дивлюсь", "Watching", "Смотрю"),
    REWATCHING("Переглядаю", "Rewatching", "Пересматриваю"),
    WATCHED("Переглянуто", "Watched", "Просмотрено"),
    ON_HOLD("Відкладено", "On hold", "Отложено"),
    DROPPED("Кинув", "Dropped", "Брошено");

    val label: String get() = tr(uk, en, ru)

    fun matches(e: LibraryEntry) = when (this) {
        ALL -> true
        FAVORITE -> e.favorite
        RATED -> e.score != null
        WANT -> e.status == WatchStatus.WANT
        WATCHING -> e.status == WatchStatus.WATCHING
        REWATCHING -> e.status == WatchStatus.REWATCHING
        WATCHED -> e.status == WatchStatus.WATCHED
        ON_HOLD -> e.status == WatchStatus.ON_HOLD
        DROPPED -> e.status == WatchStatus.DROPPED
    }
}

private enum class LibrarySort(private val uk: String, private val en: String, private val ru: String) {
    RECENT("Спочатку нові", "Newest first", "Сначала новые"),
    RATING("За моєю оцінкою", "By my rating", "По моей оценке"),
    TITLE("За назвою", "By title", "По названию"),
    YEAR("За роком", "By year", "По году");

    val label: String get() = tr(uk, en, ru)

    fun sort(list: List<LibraryEntry>): List<LibraryEntry> = when (this) {
        RECENT -> list.sortedByDescending { it.updatedAt }
        RATING -> list.sortedWith(compareByDescending<LibraryEntry> { it.score ?: -1.0 }.thenByDescending { it.updatedAt })
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
    var actorId by rememberSaveable { mutableStateOf<Int?>(null) }
    var shareList by remember { mutableStateOf(false) }
    val section = Section.fromName(sectionName)

    val bySection = all.filter { section == null || it.sectionEnum == section }
    val genresInLibrary = bySection.flatMap { it.genres }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }
    // Актёры в вашей библиотеке: чаще встречающиеся — первыми, при равенстве — кто выше в титрах.
    val actorsInLibrary = remember(bySection) {
        val names = HashMap<Int, String>()
        val count = HashMap<Int, Int>()
        val bestOrder = HashMap<Int, Int>()
        bySection.forEach { e ->
            e.cast.forEachIndexed { i, c ->
                names.putIfAbsent(c.id, c.name)
                count[c.id] = (count[c.id] ?: 0) + 1
                bestOrder[c.id] = minOf(bestOrder[c.id] ?: Int.MAX_VALUE, i)
            }
        }
        count.keys.sortedWith(compareByDescending<Int> { count[it] }.thenBy { bestOrder[it] }.thenBy { names[it] })
            .map { LibraryActor(it, names[it].orEmpty(), count[it] ?: 0) }
    }
    val actorCounts = remember(actorsInLibrary) { actorsInLibrary.associate { it.id to it.count } }
    val pinnedActors by container.ui.pinnedActors.collectAsStateWithLifecycle()
    var actorSheet by remember { mutableStateOf(false) }
    val actorName = actorsInLibrary.firstOrNull { it.id == actorId }?.name
        ?: pinnedActors.firstOrNull { it.id == actorId }?.name
        ?: all.firstNotNullOfOrNull { e -> e.cast.firstOrNull { it.id == actorId }?.name }
    val visible = sort.sort(
        bySection.filter(filter::matches)
            .filter { collection == null || collection in it.collections }
            .filter { genre == null || genre in it.genres }
            .filter { e -> actorId == null || e.cast.any { it.id == actorId } }
            .filter {
                query.isBlank() || it.displayTitle.contains(query.trim(), ignoreCase = true) ||
                    it.title.contains(query.trim(), ignoreCase = true) || it.note.contains(query.trim(), ignoreCase = true) ||
                    // Поиск и по актёрам: «Ді Капріо» найдёт его фильмы.
                    it.cast.any { c -> c.name.contains(query.trim(), ignoreCase = true) }
            }
    )
    val rated = bySection.mapNotNull { it.score }
    val stats = buildString {
        append(tr("Усього: ${bySection.size}", "Total: ${bySection.size}", "Всего: ${bySection.size}"))
        if (rated.isNotEmpty()) {
            val avg = String.format(Locale.US, "%.1f", rated.average())
            append(tr(" · Оцінено: ${rated.size} · Середня: $avg", " · Rated: ${rated.size} · Average: $avg", " · Оценено: ${rated.size} · Средняя: $avg"))
        }
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
                                placeholder = { Text(tr("Назва або нотатка…", "Title or note…", "Название или заметка…")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Default.Close, tr("Закрити пошук", "Close search", "Закрыть поиск")) }
                                },
                            )
                        } else {
                            Text(tr("Мої оцінки", "My ratings", "Мои оценки"), fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, tr("Пошук у бібліотеці", "Search library", "Поиск в библиотеке")) }
                        // Облако: вход в Google / состояние синхронизации.
                        IconButton(onClick = onAccount) {
                            when {
                                syncState.syncing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                syncState.signedIn && syncState.error == null -> Icon(Icons.Default.CloudDone, tr("Синхронізовано з Google Диском", "Synced with Google Drive", "Синхронизировано с Google Диском"))
                                syncState.signedIn -> Icon(Icons.Default.SyncProblem, tr("Помилка синхронізації", "Sync error", "Ошибка синхронизации"), tint = MaterialTheme.colorScheme.error)
                                else -> Icon(Icons.Default.CloudOff, tr("Увійти через Google", "Sign in with Google", "Войти через Google"))
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, tr("Сортування", "Sorting", "Сортировка")) }
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
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, tr("Ще", "More", "Ещё")) }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(tr("Поділитися цим списком", "Share this list", "Поделиться этим списком")) },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    enabled = visible.isNotEmpty(),
                                    onClick = { moreMenu = false; shareList = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Статистика і змійка", "Stats and snake", "Статистика и змейка")) },
                                    leadingIcon = { Icon(Icons.Default.BarChart, null) },
                                    onClick = { moreMenu = false; onStats() },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Додати фільм вручну", "Add a movie manually", "Добавить фильм вручную")) },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = { moreMenu = false; onAddManual() },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Імпорт з Letterboxd / CSV", "Import from Letterboxd / CSV", "Импорт из Letterboxd / CSV")) },
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
                    ChipRow(options, collection, { it?.let { n -> "📁 $n" } ?: tr("Усі підбірки", "All collections", "Все подборки") }, { collection = it })
                    // Выбрана подборка — её можно сразу отправить.
                    if (collection != null && visible.isNotEmpty()) {
                        TextButton(onClick = { shareList = true }, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Поділитися підбіркою «$collection»", "Share collection “$collection”", "Поделиться подборкой «$collection»"))
                        }
                    }
                }
                if (genresInLibrary.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    val options: List<String?> = listOf<String?>(null) + genresInLibrary
                    ChipRow(options, genre, { it ?: tr("Усі жанри", "All genres", "Все жанры") }, { genre = it })
                }
                if (actorsInLibrary.isNotEmpty() || pinnedActors.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    // «Усі актори» · «Вибрати актора…» · закреплённые (📌 в окне выбора).
                    ActorRow(
                        selectedId = actorId,
                        selectedName = actorName,
                        pinned = pinnedActors,
                        counts = actorCounts,
                        onAll = { actorId = null },
                        onChoose = { actorSheet = true },
                        onSelect = { actorId = it },
                    )
                }
                if (actorSheet) {
                    ActorPickerSheet(
                        actors = actorsInLibrary,
                        selectedId = actorId,
                        pinnedIds = pinnedActors.mapTo(HashSet()) { it.id },
                        onSelect = { actorId = it; actorSheet = false },
                        onTogglePin = container.ui::togglePinnedActor,
                        onDismiss = { actorSheet = false },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Змейка-питомец: уровень, нажатие — статистика.
                    val points = remember(all, collections) { snakePoints(all, collections.size) }
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
        if (shareList) {
            // Название списка: подборка, иначе раздел и фильтр.
            val listTitle = collection ?: listOfNotNull(
                section?.title,
                filter.takeIf { it != LibraryFilter.ALL }?.label,
                genre,
                actorName?.takeIf { actorId != null },
            ).joinToString(" · ").ifEmpty { tr("Мої оцінки", "My ratings", "Мои оценки") }
            ListShareDialog(title = listTitle, entries = visible, onDismiss = { shareList = false })
        }
        if (visible.isEmpty()) {
            EmptyBox(
                if (all.isEmpty()) tr(
                    "Тут з'являться фільми, які ви оціните або додасте в «Хочу подивитись».\nВідкрийте будь-який фільм і поставте зірки ⭐",
                    "Movies you rate or add to “Want to watch” will appear here.\nOpen any movie and give it stars ⭐",
                    "Здесь появятся фильмы, которые вы оцените или добавите в «Хочу посмотреть».\nОткройте любой фильм и поставьте звёзды ⭐",
                )
                else tr("Нічого не знайдено за цим фільтром", "Nothing found for this filter", "Ничего не найдено по этому фильтру"),
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
                            e.watchedEpisodeCount.takeIf { it > 0 && e.type == com.kinoteka.app.data.MediaType.TV }?.let { tr("$it сер.", "$it ep.", "$it сер.") },
                        ).joinToString(" · "),
                        tmdbRating = null,
                        myRating = e.score,
                        onClick = { onItem(e) },
                        customPoster = e.customPoster,
                    )
                }
            }
        }
    }
}
