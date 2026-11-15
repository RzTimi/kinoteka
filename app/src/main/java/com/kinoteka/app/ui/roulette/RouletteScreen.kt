package com.kinoteka.app.ui.roulette

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.data.GenreOption
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ChipRow
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.SectionChips
import com.kinoteka.app.ui.components.formatRating
import com.kinoteka.app.ui.theme.StarColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.request.ImageRequest
import com.kinoteka.app.data.tmdb.TmdbImages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RouletteSource(private val uk: String, private val en: String, private val ru: String) {
    POPULAR("Популярне", "Popular", "Популярное"),
    MY_LIST("Мій список «Хочу»", "My “Want” list", "Мой список «Хочу»");

    val label: String get() = tr(uk, en, ru)
}

enum class RuntimeFilter(private val uk: String, private val en: String, private val ru: String, val range: IntRange?) {
    ANY("Будь-яка", "Any length", "Любая", null),
    SHORT("до 1,5 год", "up to 1.5 h", "до 1,5 ч", 1..90),
    MEDIUM("1,5–2 год", "1.5–2 h", "1,5–2 ч", 90..120),
    LONG("понад 2 год", "over 2 h", "более 2 ч", 120..1000);

    val label: String get() = tr(uk, en, ru)
}

data class RouletteState(
    val source: RouletteSource = RouletteSource.POPULAR,
    val section: Section = Section.MOVIES,
    val type: MediaType = MediaType.MOVIE,
    val genres: List<GenreOption> = emptyList(),
    val genre: GenreOption? = null,
    val runtime: RuntimeFilter = RuntimeFilter.ANY,
    val spinning: Boolean = false,
    val loading: Boolean = false,
    /** Что сейчас мелькает в «барабане». */
    val shown: MediaCard? = null,
    /** Итоговый выбор змейки. */
    val result: MediaCard? = null,
    val message: String? = null,
)

class RouletteViewModel(private val repo: MediaRepository, startSection: Section) : ViewModel() {
    private val _state = MutableStateFlow(RouletteState(section = startSection, type = startSection.types.first()))
    val state = _state.asStateFlow()
    private var job: Job? = null
    /** Кэш кандидатов: при тех же фильтрах «Ще раз» крутится сразу, без загрузки. */
    private var cachedKey: String? = null
    private var cachedPool: List<MediaCard> = emptyList()

    init { loadGenres() }

    private fun poolKey(s: RouletteState) = listOf(s.source, s.section, s.type, s.genre?.name, s.runtime).joinToString("|")

    fun setSource(s: RouletteSource) = _state.update { it.copy(source = s, result = null, message = null) }

    fun setSection(s: Section) {
        _state.update { it.copy(section = s, type = s.types.first(), genre = null, result = null, message = null) }
        loadGenres()
    }

    fun setType(t: MediaType) {
        _state.update { it.copy(type = t, genre = null, result = null) }
        loadGenres()
    }

    fun setGenre(g: GenreOption?) = _state.update { it.copy(genre = g, result = null) }
    fun setRuntime(r: RuntimeFilter) = _state.update { it.copy(runtime = r, result = null) }

    private fun loadGenres() {
        val s = _state.value
        viewModelScope.launch {
            val genres = runCatching { repo.genres(s.section, s.type) }.getOrDefault(emptyList())
                .filter { it.kind == GenreOption.Kind.GENRE }
            _state.update { it.copy(genres = genres) }
        }
    }

    /** Крутим барабан: постеры мелькают всё медленнее и останавливаются на выбранном. */
    fun spin(myList: List<MediaCard>, prefetch: suspend (List<MediaCard>) -> Unit) {
        if (_state.value.spinning) return
        job?.cancel()
        job = viewModelScope.launch {
            val s = _state.value
            val key = poolKey(s)
            val cached = s.source == RouletteSource.POPULAR && key == cachedKey && cachedPool.isNotEmpty()
            _state.update { it.copy(loading = !cached, result = null, message = null) }
            val pool = if (cached) cachedPool else runCatching {
                when (s.source) {
                    RouletteSource.MY_LIST -> myList
                    RouletteSource.POPULAR -> {
                        val genreId = s.genre?.params?.get("with_genres")?.toIntOrNull()
                        repo.rouletteCandidates(s.section, s.type, genreId, s.runtime.range)
                    }
                }
            }.getOrElse { e ->
                _state.update { it.copy(loading = false, message = e.friendlyMessage()) }
                return@launch
            }
            if (s.source == RouletteSource.POPULAR) { cachedKey = key; cachedPool = pool }
            if (pool.isEmpty()) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = if (s.source == RouletteSource.MY_LIST) tr(
                            "У списку «Хочу подивитись» поки порожньо.\nПозначте фільми в їхніх картках.",
                            "Your “Want to watch” list is empty for now.\nMark movies on their pages.",
                            "В списке «Хочу посмотреть» пока пусто.\nОтметьте фильмы в их карточках.",
                        )
                        else tr("Нічого не знайдено. Спробуйте інші фільтри", "Nothing found. Try other filters", "Ничего не найдено. Попробуйте другие фильтры"),
                    )
                }
                return@launch
            }
            val winner = pool.random()
            // Заранее загружаем постеры, чтобы в «барабане» не мелькали пустые рамки.
            val reel = (pool.shuffled().take(11) + winner).distinctBy { it.key }
            if (!cached) withTimeoutOrNull(2_000) { prefetch(reel) } else prefetch(reel)
            _state.update { it.copy(loading = false, spinning = true) }
            // 18 кадров: от быстрого мелькания к медленному.
            val frames = 18
            for (i in 0 until frames) {
                val next = if (reel.size > 1) reel.filter { it.key != _state.value.shown?.key }.random() else reel.first()
                _state.update { it.copy(shown = next) }
                val t = i.toFloat() / frames
                delay((60 + 340 * t * t).toLong())
            }
            _state.update { it.copy(shown = winner, result = winner, spinning = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouletteScreen(onBack: () -> Unit, onOpen: (MediaCard) -> Unit, section: Section) {
    val repo = LocalContainer.current.repository
    val vm: RouletteViewModel = viewModel { RouletteViewModel(repo, section) }
    val state by vm.state.collectAsStateWithLifecycle()
    val library = LocalLibrary.current
    val context = LocalContext.current
    val prefetch: suspend (List<MediaCard>) -> Unit = { cards ->
        val loader = SingletonImageLoader.get(context)
        coroutineScope {
            cards.map { card ->
                async {
                    val url = TmdbImages.poster(card.posterPath, "w342") ?: return@async
                    loader.execute(ImageRequest.Builder(context).data(url).memoryCacheKey(url).build())
                }
            }.awaitAll()
        }
    }
    val myList = library.values.filter { it.status == WatchStatus.WANT }.map { e ->
        MediaCard(e.tmdbId, e.type, e.title, e.posterPath, e.year, 0.0, e.sectionEnum)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr("Що подивитись? 🐍", "What to watch? 🐍", "Что посмотреть? 🐍")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Фильтры
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(RouletteSource.entries, state.source, { it.label }, vm::setSource)
                if (state.source == RouletteSource.POPULAR) {
                    SectionChips(selected = state.section, onSelect = { it?.let(vm::setSection) })
                    if (state.section.types.size > 1) {
                        ChipRow(state.section.types, state.type, { if (it == MediaType.MOVIE) tr("Фільми", "Movies", "Фильмы") else tr("Серіали", "TV Shows", "Сериалы") }, vm::setType)
                    }
                    if (state.genres.isNotEmpty()) {
                        val options: List<GenreOption?> = listOf<GenreOption?>(null) + state.genres
                        ChipRow(options, state.genre, { it?.name ?: tr("Будь-який жанр", "Any genre", "Любой жанр") }, vm::setGenre)
                    }
                    ChipRow(RuntimeFilter.entries, state.runtime, { it.label }, vm::setRuntime)
                } else {
                    val n = myList.size
                    val what = plural(n, "фільму чи серіалу|фільмів і серіалів|фільмів і серіалів", "movie or show|movies and shows", "фильма или сериала|фильмов и сериалов|фильмов и сериалов")
                    Text(
                        tr(
                            "Змійка обере з $n $what зі статусом «Хочу подивитись»",
                            "The snake will pick from $n $what marked “Want to watch”",
                            "Змейка выберет из $n $what со статусом «Хочу посмотреть»",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // «Барабан»
            Box(Modifier.widthIn(max = 260.dp).fillMaxWidth(0.62f), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.shown != null -> AnimatedContent(
                        targetState = state.shown!!,
                        transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(90)) },
                        label = "reel",
                    ) { card ->
                        // Тот же ключ кэша, что и при предзагрузке, — постер появляется мгновенно.
                        val url = TmdbImages.poster(card.posterPath, "w342")
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).memoryCacheKey(url).build(),
                                contentDescription = card.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    else -> Column(
                        Modifier.fillMaxWidth().height(300.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🐍", style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.message ?: tr("Натисніть «Крутити», і змійка обере, що подивитись", "Tap “Spin” and the snake will pick what to watch", "Нажмите «Крутить», и змейка выберет, что посмотреть"),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.shown != null && state.message != null) {
                Text(state.message!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
            }

            // Результат
            state.result?.let { r ->
                Spacer(Modifier.height(14.dp))
                Text(r.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOfNotNull(r.section.title, r.year).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.rating > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Star, null, tint = StarColor, modifier = Modifier.size(16.dp))
                        Text(" ${formatRating(r.rating)}", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { onOpen(r) }) { Text(tr("Відкрити картку", "Open details", "Открыть карточку")) }
            }
            if (state.spinning) {
                Spacer(Modifier.height(14.dp))
                Text(tr("Змійка обирає…", "The snake is choosing…", "Змейка выбирает…"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.spin(myList, prefetch) },
                enabled = !state.spinning && !state.loading,
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Casino, null)
                Spacer(Modifier.width(10.dp))
                Text(if (state.result == null) tr("Крутити!", "Spin!", "Крутить!") else tr("Ще раз", "Again", "Ещё раз"), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
