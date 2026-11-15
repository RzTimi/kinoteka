package com.kinoteka.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.FormatQuote
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

    private val _state = MutableStateFlow<UiState<TmdbDetails>>(UiState.Loading)
    val state = _state.asStateFlow()
    val key = mediaKey(type, id)

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repo.details(type, id) }
                .fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
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
        }) { change(it.copy(title = d.displayTitle(type), posterPath = d.posterPath, year = d.year())) }
    }

    /** Повторное нажатие на ту же звезду снимает оценку. */
    fun rate(value: Int) = edit { e ->
        val newRating = if (e.rating == value) null else value
        e.copy(
            rating = newRating,
            status = if (newRating != null && (e.status == null || e.status == WatchStatus.WANT)) WatchStatus.WATCHED else e.status,
        )
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
fun DetailsScreen(route: DetailsRoute, onBack: () -> Unit) {
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
            is UiState.Success -> DetailsContent(s.data, type, entry, vm)
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
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsContent(d: TmdbDetails, type: MediaType, entry: LibraryEntry?, vm: DetailsViewModel) {
    val uri = LocalUriHandler.current
    val title = d.displayTitle(type)
    val original = (d.originalTitle ?: d.originalName)?.takeIf { it != title }
    val section = Section.classify(type, d.genres.map { it.id }, d.originalLanguage)
    var posterMenu by remember { mutableStateOf(false) }

    PosterEditor(
        open = posterMenu,
        onClose = { posterMenu = false },
        vm = vm,
        title = title,
        originalPoster = d.posterPath,
        customPoster = entry?.customPoster,
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        // Шапка: фон + постер + название
        item {
            BoxWithConstraints {
                // На телефоне фон 16:10, на широком экране не выше 300dp, чтобы не съедал всё.
                val backdropHeight = minOf(maxWidth * 10f / 16f, 300.dp)
                Box(Modifier.fillMaxWidth().height(backdropHeight)) {
                    AsyncImage(
                        model = TmdbImages.backdrop(d.backdropPath ?: d.posterPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                            Icon(Icons.Default.Edit, "Змінити постер", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).offset(y = (-4).dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                                Text("  TMDB · ${d.voteCount} голосів", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                    Text("«${d.tagline}»", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                if (!d.overview.isNullOrBlank()) Text(d.overview, style = MaterialTheme.typography.bodyLarge)
            }
        }

        val videos = youtubeVideos(d.videos?.results.orEmpty())
        if (videos.isNotEmpty()) {
            item { SectionTitle("Трейлер і відео") }
            item(key = "trailer") { TrailerSection(videos) }
        }

        val providers = d.watchProviders?.results?.get(TmdbApi.REGION)
        if (providers != null) {
            val stream = (providers.flatrate + providers.free + providers.ads).distinctBy { it.id }
            val buy = (providers.rent + providers.buy).distinctBy { it.id }.filter { p -> stream.none { it.id == p.id } }
            if (stream.isNotEmpty() || buy.isNotEmpty()) item {
                SectionTitle("Де подивитись в Україні")
                if (stream.isNotEmpty()) ProviderRow("За підпискою / безкоштовно", stream) { providers.link?.let(uri::openUri) }
                if (buy.isNotEmpty()) ProviderRow("Оренда / купівля", buy) { providers.link?.let(uri::openUri) }
                // TMDB требует указывать JustWatch как источник этих данных.
                Text(
                    "Дані: JustWatch",
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
                item { SectionTitle("Сезони") }
                items(seasons, key = { "s${it.seasonNumber}" }) { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterImage(s.posterPath ?: d.posterPath, s.name ?: "", Modifier.width(48.dp), size = "w154")
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(s.name ?: "Сезон ${s.seasonNumber}", fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(s.airDate?.take(4), "${s.episodeCount} серій").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val cast = d.credits?.cast.orEmpty().take(20)
        if (cast.isNotEmpty()) {
            item { SectionTitle("Актори") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(cast, key = { "${it.id}_${it.character}" }) { CastItem(it) }
                }
            }
        }
    }
}

private fun infoLine(d: TmdbDetails, type: MediaType, section: Section): String {
    val parts = mutableListOf(section.title)
    if (type == MediaType.MOVIE) {
        d.year()?.let(parts::add)
        d.runtime?.takeIf { it > 0 }?.let { parts += if (it >= 60) "${it / 60} год ${it % 60} хв" else "$it хв" }
    } else {
        val start = d.firstAirDate?.take(4)
        val end = d.lastAirDate?.take(4)
        if (start != null) parts += if (d.status == "Ended" || d.status == "Canceled") {
            if (end != null && end != start) "$start–$end" else start
        } else "$start–"
        d.numberOfSeasons?.let { parts += "$it сез." }
        d.numberOfEpisodes?.let { parts += "$it сер." }
        when (d.status) {
            "Returning Series" -> parts += "Виходить"
            "Ended" -> parts += "Завершено"
            "Canceled" -> parts += "Скасовано"
            "In Production" -> parts += "У виробництві"
        }
    }
    d.originCountry.firstOrNull()?.let(parts::add)
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyRatingCard(type: MediaType, entry: LibraryEntry?, vm: DetailsViewModel) {
    var noteDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Моя оцінка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    entry?.rating?.let { "$it / 10" } ?: "не оцінено",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry?.rating != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            SnakeRating(
                rating = entry?.rating,
                onRate = vm::rate,
                onClear = { entry?.rating?.let(vm::rate) },
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchStatus.entries.filter { !it.tvOnly || type == MediaType.TV }.forEach { st ->
                    FilterChip(selected = entry?.status == st, onClick = { vm.setStatus(st) }, label = { Text(st.label) })
                }
                // «Улюблене» — отдельная метка, совмещается с любым статусом.
                val fav = entry?.favorite == true
                FilterChip(
                    selected = fav,
                    onClick = vm::toggleFavorite,
                    label = { Text("Улюблене") },
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
            val note = entry?.note?.takeIf { it.isNotBlank() }
            if (note == null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { noteDialog = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Додати нотатку", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = { Text("Моя нотатка") },
            text = {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Що сподобалось, з ким дивились…") })
            },
            confirmButton = { TextButton(onClick = { vm.setNote(text); noteDialog = false }) { Text("Зберегти") } },
            dismissButton = {
                Row {
                    if (!entry?.note.isNullOrBlank()) {
                        TextButton(onClick = { vm.setNote(""); noteDialog = false }) {
                            Text("Видалити", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { noteDialog = false }) { Text("Скасувати") }
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
                    "Моя нотатка",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.Edit, "Змінити нотатку", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun CastItem(c: CastMember) {
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
