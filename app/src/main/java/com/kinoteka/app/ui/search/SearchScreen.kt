package com.kinoteka.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.PageResult
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.tmdb.Genre
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.MediaGrid
import com.kinoteka.app.ui.components.SectionChips
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/** Поиск по названию или подбор по фильтрам (когда названия нет). */
enum class SearchMode { IDLE, SEARCH, DISCOVER }

data class SearchState(
    val query: String = "",
    val section: Section? = null,
    val filters: SearchFilters = SearchFilters(),
    val mode: SearchMode = SearchMode.IDLE,
    val results: List<MediaCard> = emptyList(),
    /** Длительность найденного (для фильтра по времени при поиске по названию). */
    val runtimes: Map<String, Int?> = emptyMap(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    fun visible(library: Map<String, LibraryEntry>): List<MediaCard> = when (mode) {
        SearchMode.IDLE -> emptyList()
        // Подбор: всё уже отфильтровано на TMDB, остаётся «скрыть просмотренные».
        SearchMode.DISCOVER ->
            if (filters.hideSeen) results.filter { !SearchFilters.isSeen(library[it.key]) } else results
        SearchMode.SEARCH -> filters.sorted(
            results.filter { c ->
                (section == null || c.section == section) && filters.matches(c, runtimes[c.key], library)
            }
        )
    }
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repo: MediaRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    /** Сколько страниц догрузили сами (когда фильтры отсеяли почти всё). */
    private var autoLoads = 0

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres = _genres.asStateFlow()

    init {
        // Перезапрос — при смене названия, фильтров, а в режиме подбора и раздела.
        _state.map { s ->
            val q = s.query.trim()
            Triple(q, s.filters, if (q.length < 2) s.section else null)
        }
            .distinctUntilChanged()
            .debounce(400)
            .onEach { reload() }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            runCatching { repo.tmdbGenres(MediaType.MOVIE) }.onSuccess { _genres.value = it }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setSection(s: Section?) = _state.update { it.copy(section = s) }
    fun setFilters(f: SearchFilters) = _state.update { it.copy(filters = f) }
    fun retry() = reload()

    private fun reload() {
        job?.cancel()
        autoLoads = 0
        val s = _state.value
        val q = s.query.trim()
        val mode = when {
            q.length >= 2 -> SearchMode.SEARCH
            s.filters.narrows || s.filters.sort != SearchSort.RELEVANCE -> SearchMode.DISCOVER
            else -> SearchMode.IDLE
        }
        if (mode == SearchMode.IDLE) {
            _state.update { it.copy(mode = mode, results = emptyList(), page = 0, hasMore = false, loading = false, error = null) }
            return
        }
        _state.update { it.copy(mode = mode, loading = true, error = null, results = emptyList(), page = 0) }
        job = viewModelScope.launch { load(1) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.mode == SearchMode.IDLE || s.loading || s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { load(s.page + 1) }
    }

    /** Фильтры отсеяли почти всё — догружаем ещё пару страниц сами. */
    fun autoLoadMore() {
        if (autoLoads >= 5) return
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        autoLoads++
        loadMore()
    }

    private suspend fun load(page: Int) {
        val s = _state.value
        runCatching {
            when (s.mode) {
                SearchMode.SEARCH -> repo.search(s.query.trim(), page)
                SearchMode.DISCOVER -> discover(s.section, s.filters, page)
                SearchMode.IDLE -> PageResult(emptyList(), 1, 1)
            }
        }
            .onSuccess { r ->
                _state.update { st ->
                    val known = st.results.mapTo(HashSet()) { it.key }
                    st.copy(
                        results = st.results + r.items.filter { it.key !in known },
                        page = r.page, hasMore = r.hasMore, loading = false, loadingMore = false,
                    )
                }
                if (s.mode == SearchMode.SEARCH && s.filters.runtime != SearchRuntime.ANY) loadRuntimes()
            }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, loadingMore = false, error = e.friendlyMessage()) }
            }
    }

    /** Подбор: фильмы и сериалы раздела (или все) по параметрам фильтров. */
    private suspend fun discover(section: Section?, filters: SearchFilters, page: Int): PageResult {
        val types = section?.types ?: listOf(MediaType.MOVIE, MediaType.TV)
        val pages = kotlinx.coroutines.coroutineScope {
            types.mapNotNull { type ->
                val params = filters.discoverParams(type) ?: return@mapNotNull null
                async {
                    if (section != null) repo.discover(section, type, params, page) else repo.discoverAny(type, params, page)
                }
            }.awaitAll()
        }
        if (pages.isEmpty()) return PageResult(emptyList(), 1, 1)
        // Фильмы и сериалы вперемешку.
        val mixed = buildList {
            val max = pages.maxOf { it.items.size }
            for (i in 0 until max) pages.forEach { p -> p.items.getOrNull(i)?.let(::add) }
        }
        return PageResult(mixed, page, pages.maxOf { it.totalPages })
    }

    /** Длительность у фильмов/сериалов из выдачи (в списке поиска TMDB её нет). */
    private suspend fun loadRuntimes() {
        val s = _state.value
        // Только то, что прошло остальные фильтры: лишних запросов не делаем.
        val others = s.filters.copy(runtime = SearchRuntime.ANY, hideSeen = false)
        val missing = s.results.filter { it.key !in s.runtimes && others.matches(it, null, emptyMap()) }
        if (missing.isEmpty()) return
        val limit = Semaphore(6)
        val loaded = kotlinx.coroutines.coroutineScope {
            missing.map { c -> async { limit.withPermit { c.key to repo.runtimeOf(c.type, c.id) } } }.awaitAll()
        }
        _state.update { it.copy(runtimes = it.runtimes + loaded) }
    }

    /** Годы для ползунка. */
    companion object {
        const val MIN_YEAR = 1920
        val maxYear: Int get() = LocalDate.now().year + 2
    }
}

@Composable
fun SearchScreen(onItem: (MediaCard) -> Unit) {
    val repo = LocalContainer.current.repository
    val vm: SearchViewModel = viewModel { SearchViewModel(repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val genres by vm.genres.collectAsStateWithLifecycle()
    val library = LocalLibrary.current
    var showFilters by remember { mutableStateOf(false) }
    val visible = state.visible(library)

    // Фильтры отсеяли почти всё на загруженных страницах — подгружаем ещё.
    // page в ключах: страница из кэша грузится мгновенно, и без него эффект не заметил бы, что пора дальше.
    LaunchedEffect(visible.size, state.page, state.hasMore, state.loadingMore, state.loading, state.mode) {
        if (state.mode != SearchMode.IDLE && visible.size < 12 && state.hasMore && !state.loading && !state.loadingMore) {
            vm.autoLoadMore()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(tr("Фільм, серіал, дорама…", "Movie, TV show, drama…", "Фильм, сериал, дорама…")) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, tr("Очистити", "Clear", "Очистить")) }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                SectionChips(selected = state.section, onSelect = vm::setSection, showAll = true, baseOnly = true)
                FilterBar(state.filters, genres, onOpen = { showFilters = true }, onChange = vm::setFilters)
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.mode == SearchMode.IDLE -> IdleBox(onFilters = { showFilters = true }, onPick = vm::setFilters, modifier = modifier)
            state.loading -> LoadingBox(modifier)
            state.error != null && state.results.isEmpty() -> ErrorBox(state.error!!, vm::retry, modifier)
            visible.isEmpty() && !state.hasMore && !state.loadingMore -> NothingBox(state.filters.activeCount > 0, { vm.setFilters(SearchFilters()) }, modifier)
            else -> MediaGrid(
                items = visible,
                onItem = onItem,
                loadingMore = state.loadingMore,
                hasMore = state.hasMore,
                onLoadMore = vm::loadMore,
                modifier = modifier,
                showType = true,
            )
        }
    }

    if (showFilters) {
        FiltersSheet(
            filters = state.filters,
            genres = genres.filter { g -> g.id != 10770 && (state.section?.types ?: listOf(MediaType.MOVIE)).any { GenreMap.forType(g.id, it) != null } },
            searching = state.mode == SearchMode.SEARCH,
            onChange = vm::setFilters,
            onDismiss = { showFilters = false },
        )
    }
}

/** Кнопка «Фільтри» и включённые фильтры — каждый можно снять крестиком. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(filters: SearchFilters, genres: List<Genre>, onOpen: () -> Unit, onChange: (SearchFilters) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = filters.activeCount > 0,
                onClick = onOpen,
                label = {
                    val n = filters.activeCount
                    Text(tr("Фільтри", "Filters", "Фильтры") + if (n > 0) " · $n" else "")
                },
                leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        if (filters.hasYear) item(key = "year") {
            RemovableChip(filters.yearLabel, onOpen) { onChange(filters.copy(yearFrom = null, yearTo = null)) }
        }
        if (filters.runtime != SearchRuntime.ANY) item(key = "runtime") {
            RemovableChip("⏱ ${filters.runtime.label}", onOpen) { onChange(filters.copy(runtime = SearchRuntime.ANY)) }
        }
        if (filters.minRating != null) item(key = "rating") {
            RemovableChip("★ ${filters.minRating}+", onOpen) { onChange(filters.copy(minRating = null)) }
        }
        if (filters.genres.isNotEmpty()) item(key = "genres") {
            val names = genres.filter { it.id in filters.genres }.map { it.name.replaceFirstChar { c -> c.uppercase() } }
            val label = if (names.size <= 2 && names.isNotEmpty()) names.joinToString(", ")
            else tr("Жанри: ${filters.genres.size}", "Genres: ${filters.genres.size}", "Жанры: ${filters.genres.size}")
            RemovableChip(label, onOpen) { onChange(filters.copy(genres = emptySet())) }
        }
        if (filters.sort != SearchSort.RELEVANCE) item(key = "sort") {
            RemovableChip("↕ ${filters.sort.label}", onOpen) { onChange(filters.copy(sort = SearchSort.RELEVANCE)) }
        }
        if (filters.hideSeen) item(key = "seen") {
            RemovableChip(tr("Без переглянутого", "Hide watched", "Без просмотренного"), onOpen) { onChange(filters.copy(hideSeen = false)) }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onClick: () -> Unit, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = {
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, tr("Прибрати", "Remove", "Убрать"), Modifier.size(16.dp))
            }
        },
    )
}

/** Панель всех фильтров. Меняется сразу — результаты обновляются под ней. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    filters: SearchFilters,
    genres: List<Genre>,
    searching: Boolean,
    onChange: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr("Фільтри", "Filters", "Фильтры"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!searching) {
                Text(
                    tr(
                        "Без назви — підберемо фільми й серіали за фільтрами.",
                        "Without a title, we'll pick movies and shows by these filters.",
                        "Без названия — подберём фильмы и сериалы по фильтрам.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Год ----
            SheetTitle(tr("Рік", "Year", "Год"), filters.yearLabel)
            val maxYear = SearchViewModel.maxYear
            val from = (filters.yearFrom ?: SearchViewModel.MIN_YEAR).toFloat()
            val to = (filters.yearTo ?: maxYear).toFloat()
            RangeSlider(
                value = from..to,
                onValueChange = { r ->
                    val a = r.start.toInt()
                    val b = r.endInclusive.toInt()
                    onChange(
                        filters.copy(
                            yearFrom = a.takeIf { it > SearchViewModel.MIN_YEAR },
                            yearTo = b.takeIf { it < maxYear },
                        )
                    )
                },
                valueRange = SearchViewModel.MIN_YEAR.toFloat()..maxYear.toFloat(),
            )
            val thisYear = LocalDate.now().year
            val decades = listOf(
                tr("Цього року", "This year", "В этом году") to (thisYear to thisYear),
                "2020" + tr("-ті", "s", "-е") to (2020 to 2029),
                "2010" + tr("-ті", "s", "-е") to (2010 to 2019),
                "2000" + tr("-ті", "s", "-е") to (2000 to 2009),
                tr("90-ті", "90s", "90-е") to (1990 to 1999),
                tr("80-ті", "80s", "80-е") to (1980 to 1989),
                tr("Раніше", "Older", "Раньше") to (null to 1979),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                decades.forEach { (label, range) ->
                    val selected = filters.yearFrom == range.first && filters.yearTo == range.second
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onChange(if (selected) filters.copy(yearFrom = null, yearTo = null) else filters.copy(yearFrom = range.first, yearTo = range.second))
                        },
                        label = { Text(label) },
                    )
                }
            }

            // ---- Длительность ----
            SheetTitle(tr("Тривалість", "Length", "Длительность"), null)
            Text(
                tr("У серіалів — тривалість однієї серії.", "For TV shows — length of one episode.", "У сериалов — длительность одной серии."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchRuntime.entries.forEach { r ->
                    FilterChip(selected = filters.runtime == r, onClick = { onChange(filters.copy(runtime = r)) }, label = { Text(r.label) })
                }
            }

            // ---- Рейтинг ----
            SheetTitle(tr("Рейтинг TMDB", "TMDB rating", "Рейтинг TMDB"), null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filters.minRating == null, onClick = { onChange(filters.copy(minRating = null)) }, label = { Text(tr("Будь-який", "Any", "Любой")) })
                listOf(6, 7, 8).forEach { r ->
                    FilterChip(selected = filters.minRating == r, onClick = { onChange(filters.copy(minRating = r)) }, label = { Text("★ $r+") })
                }
            }

            // ---- Жанры ----
            if (genres.isNotEmpty()) {
                SheetTitle(tr("Жанри", "Genres", "Жанры"), null)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { g ->
                        val on = g.id in filters.genres
                        FilterChip(
                            selected = on,
                            onClick = { onChange(filters.copy(genres = if (on) filters.genres - g.id else filters.genres + g.id)) },
                            label = { Text(g.name.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            // ---- Сортировка ----
            SheetTitle(tr("Сортування", "Sort", "Сортировка"), null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchSort.entries.forEach { s ->
                    FilterChip(selected = filters.sort == s, onClick = { onChange(filters.copy(sort = s)) }, label = { Text(s.label) })
                }
            }

            // ---- Скрыть просмотренное ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Сховати переглянуте", "Hide watched", "Скрыть просмотренное"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Те, що вже оцінено або переглянуто", "Already rated or watched", "То, что уже оценено или просмотрено"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = filters.hideSeen, onCheckedChange = { onChange(filters.copy(hideSeen = it)) })
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(SearchFilters()) }, enabled = filters.activeCount > 0) {
                    Text(tr("Скинути все", "Reset all", "Сбросить всё"))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text(tr("Показати", "Show", "Показать")) }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, value: String?) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Пустой экран: подсказка и быстрые подборки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleBox(onFilters: () -> Unit, onPick: (SearchFilters) -> Unit, modifier: Modifier) {
    val thisYear = LocalDate.now().year
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            tr(
                "Введіть назву — або підберіть за роком, тривалістю, жанром і рейтингом",
                "Enter a title — or pick by year, length, genre and rating",
                "Введите название — или подберите по году, длительности, жанру и рейтингу",
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onFilters) {
            Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Підібрати за фільтрами", "Pick with filters", "Подобрать по фильтрам"))
        }
        Spacer(Modifier.height(4.dp))
        Text(tr("Швидкі підбірки", "Quick picks", "Быстрые подборки"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val picks = listOf(
            tr("Новинки $thisYear", "New in $thisYear", "Новинки $thisYear") to SearchFilters(yearFrom = thisYear, yearTo = thisYear, sort = SearchSort.POPULAR),
            tr("★ 8+ за весь час", "★ 8+ of all time", "★ 8+ за всё время") to SearchFilters(minRating = 8, sort = SearchSort.RATING),
            tr("На вечір, до 1,5 год", "For tonight, under 1.5 h", "На вечер, до 1,5 ч") to SearchFilters(runtime = SearchRuntime.SHORT, minRating = 7, sort = SearchSort.POPULAR),
            tr("Комедії 2010-х", "2010s comedies", "Комедии 2010-х") to SearchFilters(yearFrom = 2010, yearTo = 2019, genres = setOf(35), sort = SearchSort.POPULAR),
            tr("Жахи з високим рейтингом", "Top-rated horror", "Ужасы с высоким рейтингом") to SearchFilters(genres = setOf(27), minRating = 7, sort = SearchSort.RATING),
            tr("Ще не переглянуте", "Not seen yet", "Ещё не просмотренное") to SearchFilters(minRating = 7, sort = SearchSort.POPULAR, hideSeen = true),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            picks.forEach { (label, f) ->
                FilterChip(selected = false, onClick = { onPick(f) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun NothingBox(hasFilters: Boolean, onReset: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            tr("Нічого не знайдено", "Nothing found", "Ничего не найдено"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasFilters) OutlinedButton(onClick = onReset) { Text(tr("Скинути фільтри", "Reset filters", "Сбросить фильтры")) }
    }
}
