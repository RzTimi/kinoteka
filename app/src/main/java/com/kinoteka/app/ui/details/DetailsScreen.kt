package com.kinoteka.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.kinoteka.app.data.library.formatScore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.LibraryStore
import com.kinoteka.app.data.library.PosterStorage
import com.kinoteka.app.data.tmdb.ImageInfo
import android.graphics.Bitmap
import android.graphics.Rect
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.data.mediaKey
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.toCard
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.components.MediaRow
import com.kinoteka.app.data.tmdb.CastMember
import com.kinoteka.app.data.tmdb.Provider
import com.kinoteka.app.data.tmdb.TmdbApi
import com.kinoteka.app.data.tmdb.TmdbDetails
import com.kinoteka.app.data.tmdb.TmdbImages
import com.kinoteka.app.ui.DetailsRoute
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.PosterImage
import com.kinoteka.app.ui.components.UiState
import com.kinoteka.app.ui.components.formatRating
import com.kinoteka.app.ui.theme.StarColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val repo: MediaRepository,
    private val library: LibraryStore,
    private val posterStorage: PosterStorage,
    val type: MediaType,
    val id: Int,
) : ViewModel() {
    private val _altPosters = MutableStateFlow<UiState<List<ImageInfo>>>(UiState.Loading)
    /** Другие постеры с TMDB (грузятся при первом открытии выбора). */
    val altPosters = _altPosters.asStateFlow()
    private var altPostersRequested = false

    fun loadAltPosters(force: Boolean = false) {
        if (altPostersRequested && !force) return
        altPostersRequested = true
        _altPosters.value = UiState.Loading
        viewModelScope.launch {
            _altPosters.value = runCatching { repo.posters(type, id) }
                .fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
        }
    }

    private fun setPoster(ref: String?) {
        val old = library.entries.value[key]?.customPoster
        edit { it.copy(customPoster = ref) }
        if (old != ref) posterStorage.deleteIfLocal(old)
    }

    fun useTmdbPoster(path: String) = setPoster(PosterStorage.TMDB_PREFIX + path)
    fun resetPoster() = setPoster(null)

    fun saveGalleryPoster(bitmap: Bitmap, rect: Rect) {
        viewModelScope.launch { setPoster(posterStorage.saveCropped(bitmap, rect, key)) }
    }

    // ---------- Свой фон ----------

    private val _altBackdrops = MutableStateFlow<UiState<List<ImageInfo>>>(UiState.Loading)
    val altBackdrops = _altBackdrops.asStateFlow()
    private var altBackdropsRequested = false

    fun loadAltBackdrops(force: Boolean = false) {
        if (altBackdropsRequested && !force) return
        altBackdropsRequested = true
        _altBackdrops.value = UiState.Loading
        viewModelScope.launch {
            _altBackdrops.value = runCatching { repo.backdrops(type, id) }
                .fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
        }
    }

    private fun setBackdrop(ref: String?) {
        val old = library.entries.value[key]?.customBackdrop
        edit { it.copy(customBackdrop = ref) }
        if (old != ref) posterStorage.deleteIfLocal(old)
    }

    fun useTmdbBackdrop(path: String) = setBackdrop(PosterStorage.TMDB_PREFIX + path)
    fun resetBackdrop() = setBackdrop(null)

    fun saveGalleryBackdrop(bitmap: Bitmap, rect: Rect) {
        viewModelScope.launch {
            setBackdrop(posterStorage.saveCropped(bitmap, rect, key, maxWidth = 1280, aspect = 16f / 9f, prefix = "bg_"))
        }
    }

    private val _state = MutableStateFlow<UiState<TmdbDetails>>(UiState.Loading)
    val state = _state.asStateFlow()
    val key = mediaKey(type, id)

    init { load() }

    /** Рейтинг IMDb (если задан ключ OMDb): «8.2» и число голосов. */
    private val _imdb = MutableStateFlow<Pair<String, String>?>(null)
    val imdb = _imdb.asStateFlow()

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val result = runCatching { repo.details(type, id) }
            _state.value = result.fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
            result.getOrNull()?.externalIds?.imdbId?.takeIf { it.isNotBlank() }?.let { imdbId ->
                _imdb.value = repo.imdbRating(imdbId)
            }
        }
    }

    private fun edit(change: (LibraryEntry) -> LibraryEntry) {
        val d = (state.value as? UiState.Success)?.data ?: return
        library.update(key, create = {
            LibraryEntry(
                mediaType = type.path,
                tmdbId = id,
                title = d.displayTitle(type),
                posterPath = d.posterPath,
                year = d.year(),
                section = Section.classify(type, d.genres.map { it.id }, d.originalLanguage).name,
            )
        }) { old ->
            val runtime = d.runtime?.takeIf { it > 0 } ?: d.episodeRunTime.firstOrNull()
            val changed = change(
                old.copy(
                    title = d.displayTitle(type),
                    posterPath = d.posterPath,
                    year = d.year(),
                    genres = d.genres.map { g -> g.name.replaceFirstChar { c -> c.uppercase() } },
                    runtime = runtime ?: old.runtime,
                )
            )
            // Дата просмотра — для статистики по месяцам.
            val nowWatched = changed.status == WatchStatus.WATCHED || changed.rating != null
            changed.copy(watchedAt = if (nowWatched) changed.watchedAt ?: System.currentTimeMillis() else changed.watchedAt)
        }
    }

    // ---------- Подборки ----------

    fun toggleCollection(name: String) = edit { e ->
        e.copy(collections = if (name in e.collections) e.collections - name else e.collections + name)
    }

    fun createCollectionAndAdd(name: String) {
        library.createCollection(name)
        edit { e -> if (name.trim() in e.collections) e else e.copy(collections = e.collections + name.trim()) }
    }

    // ---------- Своё название ----------

    fun setCustomTitle(title: String?) = edit { e -> e.copy(customTitle = title?.trim()?.takeIf { it.isNotEmpty() }) }

    // ---------- Трекер серий ----------

    /** Отметить/снять серию. Статус «Дивлюсь»/«Переглянуто» ставится сам. */
    fun toggleEpisode(season: Int, episode: Int) = edit { e ->
        val key = season.toString()
        val current = e.episodes[key].orEmpty()
        val updated = if (episode in current) current - episode else (current + episode).sorted()
        withEpisodeStatus(e.copy(episodes = (e.episodes + (key to updated)).filterValues { it.isNotEmpty() }))
    }

    /** Отметить весь сезон (или снять, если он уже весь отмечен). */
    fun toggleSeason(season: Int, episodeCount: Int) = edit { e ->
        val key = season.toString()
        val all = (1..episodeCount).toList()
        val full = e.episodes[key].orEmpty().containsAll(all)
        val updated = if (full) e.episodes - key else e.episodes + (key to all)
        withEpisodeStatus(e.copy(episodes = updated))
    }

    private fun withEpisodeStatus(e: LibraryEntry): LibraryEntry {
        val d = (state.value as? UiState.Success)?.data ?: return e
        val total = d.seasons.filter { it.seasonNumber > 0 }.sumOf { it.episodeCount }
        val watched = e.watchedEpisodeCount
        // Статус меняем только из «пусто»/«Хочу» в «Дивлюсь» и обратно; «Переглянуто» и другие не трогаем.
        val status = when {
            watched == 0 -> e.status.takeIf { it != WatchStatus.WATCHING }
            total > 0 && watched >= total && d.status in setOf("Ended", "Canceled") -> WatchStatus.WATCHED
            e.status == null || e.status == WatchStatus.WANT -> WatchStatus.WATCHING
            else -> e.status
        }
        return e.copy(status = status)
    }

    /** Повторное нажатие на ту же звезду снимает оценку. */
    /** Поставить оценку с десятыми (null — снять). Статус «Переглянуто» ставится сам. */
    fun setScore(value: Double?) = edit { e ->
        e.withScore(value).copy(
            status = if (value != null && (e.status == null || e.status == WatchStatus.WANT)) WatchStatus.WATCHED else e.status,
        )
    }

    /** Подстройка ±0,1. */
    fun nudgeScore(delta: Double) {
        val current = library.entries.value[key]?.score ?: 5.0
        setScore((current + delta).coerceIn(1.0, 10.0))
    }

    fun setStatus(status: WatchStatus) = edit { e -> e.copy(status = if (e.status == status) null else status) }
    fun toggleFavorite() = edit { e -> e.copy(favorite = !e.favorite) }
    fun setNote(note: String) = edit { e -> e.copy(note = note.trim()) }
}

val FavoriteRed = Color(0xFFE53950)

fun TmdbDetails.displayTitle(type: MediaType): String =
    (if (type == MediaType.MOVIE) title ?: name else name ?: title) ?: originalTitle ?: originalName ?: "—"

fun TmdbDetails.year(): String? = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.isNotBlank() }

@Composable
fun DetailsScreen(
    route: DetailsRoute,
    onBack: () -> Unit,
    onOpen: (MediaCard) -> Unit,
    onPerson: (Int) -> Unit,
    onSeason: (Int) -> Unit = {},
) {
    val container = LocalContainer.current
    val type = MediaType.fromPath(route.type) ?: MediaType.MOVIE
    val vm: DetailsViewModel = viewModel {
        DetailsViewModel(container.repository, container.library, container.posters, type, route.id)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val entry = LocalLibrary.current[vm.key]

    Surface(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(s.message, vm::load)
            is UiState.Success -> DetailsContent(s.data, type, entry, vm, onOpen, onPerson, onSeason)
        }
        BackButton(onBack)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(Modifier.statusBarsPadding().padding(8.dp)) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(
    d: TmdbDetails,
    type: MediaType,
    entry: LibraryEntry?,
    vm: DetailsViewModel,
    onOpen: (MediaCard) -> Unit,
    onPerson: (Int) -> Unit,
    onSeason: (Int) -> Unit,
) {
    val uri = LocalUriHandler.current
    val imdb by vm.imdb.collectAsStateWithLifecycle()
    val imdbId = d.externalIds?.imdbId?.takeIf { it.isNotBlank() }
    val tmdbTitle = d.displayTitle(type)
    val title = entry?.customTitle?.takeIf { it.isNotBlank() } ?: tmdbTitle
    // Под своим названием показываем название TMDB, иначе — оригинальное.
    val original = if (title != tmdbTitle) tmdbTitle else (d.originalTitle ?: d.originalName)?.takeIf { it != title }
    val section = Section.classify(type, d.genres.map { it.id }, d.originalLanguage)
    var posterMenu by remember { mutableStateOf(false) }
    var backdropMenu by remember { mutableStateOf(false) }
    var titleDialog by remember { mutableStateOf(false) }
    var shareDialog by remember { mutableStateOf(false) }
    val filesDir = LocalContext.current.filesDir

    PosterEditor(
        open = posterMenu,
        onClose = { posterMenu = false },
        vm = vm,
        title = title,
        originalPoster = d.posterPath,
        customPoster = entry?.customPoster,
    )
    PosterEditor(
        open = backdropMenu,
        onClose = { backdropMenu = false },
        vm = vm,
        title = title,
        originalPoster = d.backdropPath,
        customPoster = entry?.customBackdrop,
        backdrop = true,
    )
    if (titleDialog) {
        CustomTitleDialog(
            current = entry?.customTitle ?: tmdbTitle,
            hasCustom = entry?.customTitle != null,
            onSave = { vm.setCustomTitle(it); titleDialog = false },
            onReset = { vm.setCustomTitle(null); titleDialog = false },
            onDismiss = { titleDialog = false },
        )
    }
    if (shareDialog) {
        ShareCardDialog(
            title = title,
            year = d.year(),
            posterModel = PosterStorage.model(filesDir, entry?.customPoster, d.posterPath, "w500"),
            rating = entry?.score,
            note = entry?.note,
            shareText = com.kinoteka.app.ui.share.movieShareText(title, d.year(), type, d.id, entry?.score, entry?.note),
            onDismiss = { shareDialog = false },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        // Шапка: фон + постер + название
        item {
            BoxWithConstraints {
                // На телефоне фон 16:10, на широком экране не выше 300dp, чтобы не съедал всё.
                val backdropHeight = minOf(maxWidth * 10f / 16f, 300.dp)
                Box(Modifier.fillMaxWidth().height(backdropHeight)) {
                    AsyncImage(
                        model = PosterStorage.model(filesDir, entry?.customBackdrop, d.backdropPath ?: d.posterPath, "w1280"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Кнопки на фоне: поделиться и сменить фон.
                    Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                        IconButton(
                            onClick = { shareDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
                        ) { Icon(Icons.Default.Share, tr("Поділитися", "Share", "Поделиться")) }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { backdropMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
                        ) { Icon(Icons.Default.Wallpaper, tr("Змінити фон", "Change background", "Изменить фон")) }
                    }
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.45f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
                }
                Row(
                    Modifier.padding(top = backdropHeight - 80.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Нажатие на постер: сменить на свой.
                    Box(Modifier.width(118.dp).clip(RoundedCornerShape(10.dp)).clickable { posterMenu = true }) {
                        PosterImage(d.posterPath, title, Modifier.fillMaxWidth(), size = "w500", custom = entry?.customPoster)
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp).size(30.dp)
                                .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Edit, tr("Змінити постер", "Change poster", "Изменить постер"), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).offset(y = (-4).dp)) {
                        // Нажатие на название — задать своё.
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable { titleDialog = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Edit, tr("Змінити назву", "Edit title", "Изменить название"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (original != null) {
                            Text(original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(infoLine(d, type, section), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (d.voteAverage > 0) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = StarColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(formatRating(d.voteAverage), fontWeight = FontWeight.Bold)
                                Text("  TMDB · ${d.voteCount} ${plural(d.voteCount, "голос|голоси|голосів", "vote|votes", "голос|голоса|голосов")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Ваша оценка — рядом с TMDB и IMDb.
                        entry?.score?.let { my ->
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🐍", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(4.dp))
                                Text(formatScore(my), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("  " + tr("моя оцінка", "my rating", "моя оценка"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (imdbId != null) {
                            Spacer(Modifier.height(6.dp))
                            ImdbBadge(imdb) { uri.openUri("https://www.imdb.com/title/$imdbId/") }
                        }
                    }
                }
            }
        }

        if (d.genres.isNotEmpty()) item {
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                d.genres.forEach { g -> SuggestionChip(onClick = {}, label = { Text(g.name.replaceFirstChar { it.uppercase() }) }) }
            }
        }

        // Ваша оценка
        item { MyRatingCard(type, entry, vm) }

        if (!d.tagline.isNullOrBlank() || !d.overview.isNullOrBlank()) item {
            Column(Modifier.padding(16.dp)) {
                if (!d.tagline.isNullOrBlank()) {
                    Text(tr("«${d.tagline}»", "“${d.tagline}”", "«${d.tagline}»"), fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                if (!d.overview.isNullOrBlank()) Text(d.overview, style = MaterialTheme.typography.bodyLarge)
            }
        }

        val videos = youtubeVideos(d.videos?.results.orEmpty())
        if (videos.isNotEmpty()) {
            item { SectionTitle(tr("Трейлер і відео", "Trailer and videos", "Трейлер и видео")) }
            item(key = "trailer") { TrailerSection(videos) }
        }

        val providers = d.watchProviders?.results?.get(TmdbApi.REGION)
        if (providers != null) {
            val stream = (providers.flatrate + providers.free + providers.ads).distinctBy { it.id }
            val buy = (providers.rent + providers.buy).distinctBy { it.id }.filter { p -> stream.none { it.id == p.id } }
            if (stream.isNotEmpty() || buy.isNotEmpty()) item {
                SectionTitle(tr("Де подивитись в Україні", "Where to watch in Ukraine", "Где посмотреть в Украине"))
                if (stream.isNotEmpty()) ProviderRow(tr("За підпискою / безкоштовно", "Subscription / free", "По подписке / бесплатно"), stream) { providers.link?.let(uri::openUri) }
                if (buy.isNotEmpty()) ProviderRow(tr("Оренда / купівля", "Rent / buy", "Аренда / покупка"), buy) { providers.link?.let(uri::openUri) }
                // TMDB требует указывать JustWatch как источник этих данных.
                Text(
                    tr("Дані: JustWatch", "Data: JustWatch", "Данные: JustWatch"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { uri.openUri("https://www.justwatch.com/") },
                )
            }
        }

        if (type == MediaType.TV) {
            val seasons = d.seasons.filter { it.seasonNumber > 0 }
            if (seasons.isNotEmpty()) {
                item { SectionTitle(tr("Сезони і серії", "Seasons and episodes", "Сезоны и серии")) }
                item { EpisodeProgress(d, entry, vm) }
                items(seasons, key = { "s${it.seasonNumber}" }) { s ->
                    val done = entry?.episodes?.get(s.seasonNumber.toString())?.size ?: 0
                    Row(
                        Modifier.fillMaxWidth().clickable { onSeason(s.seasonNumber) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(s.posterPath ?: d.posterPath, s.name ?: "", Modifier.width(48.dp), size = "w154")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name ?: tr("Сезон ${s.seasonNumber}", "Season ${s.seasonNumber}", "Сезон ${s.seasonNumber}"), fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(s.airDate?.take(4), "${s.episodeCount} ${plural(s.episodeCount, "серія|серії|серій", "episode|episodes", "серия|серии|серий")}").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            s.episodeCount > 0 && done >= s.episodeCount ->
                                Icon(Icons.Default.CheckCircle, tr("Сезон переглянуто", "Season watched", "Сезон просмотрен"), tint = MaterialTheme.colorScheme.primary)
                            done > 0 -> Text("$done/${s.episodeCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val cast = d.credits?.cast.orEmpty().take(20)
        if (cast.isNotEmpty()) {
            item { SectionTitle(tr("Актори", "Cast", "Актёры")) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cast, key = { "${it.id}_${it.character}" }) { c -> CastItem(c) { onPerson(c.id) } }
                }
            }
        }

        // «Рекомендації» (TMDB по оценкам зрителей) и «Схожі» (по жанрам и ключевым словам).
        val recs = d.recommendations?.results.orEmpty()
            .mapNotNull { item -> (MediaType.fromPath(item.mediaType) ?: type).let { t -> item.toCard(t) } }
            .filter { it.posterPath != null }
        val similar = d.similar?.results.orEmpty()
            .map { it.toCard(type) }
            .filter { it.posterPath != null && recs.none { r -> r.key == it.key } }
        if (recs.isNotEmpty()) item {
            Spacer(Modifier.height(20.dp))
            MediaRow(tr("Рекомендації", "Recommendations", "Рекомендации"), recs, onOpen, onMore = null)
        }
        if (similar.isNotEmpty()) item {
            Spacer(Modifier.height(20.dp))
            MediaRow(tr("Схожі", "Similar", "Похожие"), similar, onOpen, onMore = null)
        }
    }
}

private fun infoLine(d: TmdbDetails, type: MediaType, section: Section): String {
    val parts = mutableListOf(section.title)
    if (type == MediaType.MOVIE) {
        d.year()?.let(parts::add)
        d.runtime?.takeIf { it > 0 }?.let { parts += if (it >= 60) tr("${it / 60} год ${it % 60} хв", "${it / 60} h ${it % 60} min", "${it / 60} ч ${it % 60} мин") else tr("$it хв", "$it min", "$it мин") }
    } else {
        val start = d.firstAirDate?.take(4)
        val end = d.lastAirDate?.take(4)
        if (start != null) parts += if (d.status == "Ended" || d.status == "Canceled") {
            if (end != null && end != start) "$start–$end" else start
        } else "$start–"
        d.numberOfSeasons?.let { parts += tr("$it сез.", "$it seas.", "$it сез.") }
        d.numberOfEpisodes?.let { parts += tr("$it сер.", "$it ep.", "$it сер.") }
        when (d.status) {
            "Returning Series" -> parts += tr("Виходить", "Ongoing", "Выходит")
            "Ended" -> parts += tr("Завершено", "Ended", "Завершён")
            "Canceled" -> parts += tr("Скасовано", "Canceled", "Отменён")
            "In Production" -> parts += tr("У виробництві", "In production", "В производстве")
        }
    }
    d.originCountry.firstOrNull()?.let(parts::add)
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyRatingCard(type: MediaType, entry: LibraryEntry?, vm: DetailsViewModel) {
    var noteDialog by remember { mutableStateOf(false) }
    val score = entry?.score
    // Уже что-то выставлено — показываем компактную сводку, полное меню по кнопке «Змінити».
    val hasData = score != null || entry?.status != null || entry?.favorite == true || !entry?.collections.isNullOrEmpty()
    var editing by rememberSaveable(vm.key) { mutableStateOf(!hasData) }
    val expanded = editing || !hasData

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Моя оцінка", "My rating", "Моя оценка"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    score?.let { "${formatScore(it)} / 10" } ?: tr("не оцінено", "not rated", "не оценено"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (score != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                if (hasData) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { editing = !expanded }) {
                        if (!expanded) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(if (expanded) tr("Готово", "Done", "Готово") else tr("Змінити", "Edit", "Изменить"))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (!expanded) {
                // ---- Компактный вид: змейка и только выбранные плашки ----
                SnakeRating(rating = score, onRate = {}, onClear = {}, interactive = false, height = 48.dp)
                val selected = buildList {
                    entry?.status?.let { add(it.label) }
                    if (entry?.favorite == true) add(tr("❤ Улюблене", "❤ Favorite", "❤ Избранное"))
                    entry?.collections?.forEach { add("📁 $it") }
                }
                if (selected.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        selected.forEach { label ->
                            FilterChip(selected = true, onClick = { editing = true }, label = { Text(label) })
                        }
                    }
                }
            } else {
                // ---- Полное меню ----
                SnakeRating(rating = score, onRate = vm::setScore, onClear = { vm.setScore(null) })
                Text(
                    tr(
                        "Натисніть на змійку — ціла оцінка, проведіть пальцем — з десятими.",
                        "Tap the snake for a whole rating, swipe for tenths.",
                        "Нажмите на змейку — целая оценка, проведите пальцем — с десятыми.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Точная подстройка ±0,1.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.nudgeScore(-0.1) }, enabled = score != null && score > 1.0) { Text(tr("−0,1", "−0.1", "−0,1")) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { vm.nudgeScore(+0.1) }, enabled = score == null || score < 10.0) { Text(tr("+0,1", "+0.1", "+0,1")) }
                    Spacer(Modifier.weight(1f))
                    if (score != null) TextButton(onClick = { vm.setScore(null) }) { Text(tr("Скинути", "Reset", "Сбросить")) }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatchStatus.entries.filter { !it.tvOnly || type == MediaType.TV }.forEach { st ->
                        FilterChip(selected = entry?.status == st, onClick = { vm.setStatus(st) }, label = { Text(st.label) })
                    }
                    // «Улюблене» — отдельная метка, совмещается с любым статусом.
                    val fav = entry?.favorite == true
                    FilterChip(
                        selected = fav,
                        onClick = vm::toggleFavorite,
                        label = { Text(tr("Улюблене", "Favorite", "Избранное")) },
                        leadingIcon = {
                            Icon(
                                if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null,
                                Modifier.size(FilterChipDefaults.IconSize),
                                tint = FavoriteRed,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = FavoriteRed.copy(alpha = 0.15f)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                CollectionsRow(
                    current = entry?.collections.orEmpty(),
                    onToggle = vm::toggleCollection,
                    onCreate = vm::createCollectionAndAdd,
                )
            }
            val note = entry?.note?.takeIf { it.isNotBlank() }
            if (note == null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { noteDialog = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Додати нотатку", "Add a note", "Добавить заметку"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(16.dp))
                NoteBlock(note) { noteDialog = true }
            }
        }
    }
    if (noteDialog) {
        var text by remember { mutableStateOf(entry?.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text(tr("Моя нотатка", "My note", "Моя заметка")) },
            text = {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text(tr("Що сподобалось, з ким дивились…", "What you liked, who you watched it with…", "Что понравилось, с кем смотрели…")) })
            },
            confirmButton = { TextButton(onClick = { vm.setNote(text); noteDialog = false }) { Text(tr("Зберегти", "Save", "Сохранить")) } },
            dismissButton = {
                Row {
                    if (!entry?.note.isNullOrBlank()) {
                        TextButton(onClick = { vm.setNote(""); noteDialog = false }) {
                            Text(tr("Видалити", "Delete", "Удалить"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { noteDialog = false }) { Text(tr("Скасувати", "Cancel", "Отмена")) }
                }
            },
        )
    }
}

/** Ваша нотатка: отдельный выделенный блок-цитата с акцентной полосой слева. */
@Composable
private fun NoteBlock(note: String, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onEdit),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatQuote, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    tr("Моя нотатка", "My note", "Моя заметка"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.Edit, tr("Змінити нотатку", "Edit note", "Изменить заметку"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProviderRow(label: String, providers: List<Provider>, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(providers, key = { it.id }) { p ->
                Column(Modifier.width(64.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = TmdbImages.logo(p.logoPath),
                        contentDescription = p.name,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Диалог «Своя назва». */
@Composable
private fun CustomTitleDialog(current: String, hasCustom: Boolean, onSave: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Своя назва", "Custom title", "Своё название")) },
        text = {
            Column {
                Text(tr("Наприклад, оригінальна назва або як ви його називаєте.", "For example, the original title or what you call it.", "Например, оригинальное название или как вы его называете."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(tr("Зберегти", "Save", "Сохранить")) } },
        dismissButton = {
            Row {
                if (hasCustom) TextButton(onClick = onReset) { Text(tr("Скинути", "Reset", "Сбросить")) }
                TextButton(onClick = onDismiss) { Text(tr("Скасувати", "Cancel", "Отмена")) }
            }
        },
    )
}

/** Прогресс сериала: сколько серий просмотрено и кнопка «следующая серия». */
@Composable
private fun EpisodeProgress(d: TmdbDetails, entry: LibraryEntry?, vm: DetailsViewModel) {
    val seasons = d.seasons.filter { it.seasonNumber > 0 && it.episodeCount > 0 }
    val total = seasons.sumOf { it.episodeCount }
    val watched = entry?.watchedEpisodeCount ?: 0
    // Следующая непросмотренная серия, но не дальше последней вышедшей.
    val last = d.lastEpisode
    val next = seasons.asSequence().flatMap { s ->
        (1..s.episodeCount).asSequence().map { e -> s.seasonNumber to e }
    }.firstOrNull { (s, e) -> e !in entry?.episodes?.get(s.toString()).orEmpty() }
        ?.takeIf { (s, e) -> last == null || s < last.seasonNumber || (s == last.seasonNumber && e <= last.episodeNumber) }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Переглянуто $watched з $total", "Watched $watched of $total", "Просмотрено $watched из $total") + " " + plural(total, "серії|серій|серій", "episode|episodes", "серии|серий|серий"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (total > 0) Text("${watched * 100 / total}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else watched.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { vm.toggleEpisode(next.first, next.second) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Переглянув S${next.first}E${next.second}", "Watched S${next.first}E${next.second}", "Посмотрел S${next.first}E${next.second}"))
                }
            } else if (watched > 0 && d.nextEpisode != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Наступна серія S${d.nextEpisode.seasonNumber}E${d.nextEpisode.episodeNumber} вийде ${d.nextEpisode.airDate.orEmpty()}",
                        "Next episode S${d.nextEpisode.seasonNumber}E${d.nextEpisode.episodeNumber} airs ${d.nextEpisode.airDate.orEmpty()}",
                        "Следующая серия S${d.nextEpisode.seasonNumber}E${d.nextEpisode.episodeNumber} выйдет ${d.nextEpisode.airDate.orEmpty()}",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Жёлтый значок IMDb: с оценкой, если есть ключ OMDb, иначе просто ссылка. */
@Composable
private fun ImdbBadge(rating: Pair<String, String>?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF5C518)).padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text("IMDb", color = Color.Black, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(6.dp))
        if (rating != null) {
            Text(rating.first, fontWeight = FontWeight.Bold)
            if (rating.second.isNotBlank()) {
                val votes = rating.second.filter { it.isDigit() }.toIntOrNull() ?: 0
                Text("  · ${rating.second} ${plural(votes, "голос|голоси|голосів", "vote|votes", "голос|голоса|голосов")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(tr("Відкрити на IMDb", "Open on IMDb", "Открыть на IMDb"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CastItem(c: CastMember, onClick: () -> Unit) {
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (c.profilePath != null) {
                AsyncImage(TmdbImages.profile(c.profilePath), c.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center)
        if (!c.character.isNullOrBlank()) {
            Text(c.character, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}
