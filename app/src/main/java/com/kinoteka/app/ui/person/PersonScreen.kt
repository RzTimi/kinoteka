package com.kinoteka.app.ui.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.PersonPage
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.data.tmdb.PersonDetails
import com.kinoteka.app.data.tmdb.TmdbImages
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ChipRow
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.MediaRow
import com.kinoteka.app.ui.components.PosterCard
import com.kinoteka.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class PersonViewModel(private val repo: MediaRepository, private val id: Int) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PersonPage>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repo.person(id) }
                .fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
        }
    }
}

private enum class CreditFilter(private val uk: String, private val en: String, private val ru: String) {
    ALL("Усе", "All", "Все"),
    MOVIES("Фільми", "Movies", "Фильмы"),
    TV("Серіали", "TV Shows", "Сериалы");

    val label: String get() = tr(uk, en, ru)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(id: Int, onBack: () -> Unit, onOpen: (MediaCard) -> Unit) {
    val repo = LocalContainer.current.repository
    val vm: PersonViewModel = viewModel { PersonViewModel(repo, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val title = (state as? UiState.Success)?.data?.person?.name.orEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = state) {
            UiState.Loading -> LoadingBox(modifier)
            is UiState.Error -> ErrorBox(s.message, vm::load, modifier)
            is UiState.Success -> PersonContent(s.data, onOpen, modifier)
        }
    }
}

@Composable
private fun PersonContent(page: PersonPage, onOpen: (MediaCard) -> Unit, modifier: Modifier) {
    var filter by rememberSaveable { mutableStateOf(CreditFilter.ALL) }
    val library = LocalLibrary.current
    val credits = page.credits.filter {
        when (filter) {
            CreditFilter.ALL -> true
            CreditFilter.MOVIES -> it.card.type == MediaType.MOVIE
            CreditFilter.TV -> it.card.type == MediaType.TV
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { PersonHeader(page.person) }

        if (page.knownFor.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            MediaRow(tr("Відомі роботи", "Known for", "Известные работы"), page.knownFor, onOpen, onMore = null)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    tr("Фільмографія", "Filmography", "Фильмография"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                val counts = mapOf(
                    CreditFilter.ALL to page.credits.size,
                    CreditFilter.MOVIES to page.credits.count { it.card.type == MediaType.MOVIE },
                    CreditFilter.TV to page.credits.count { it.card.type == MediaType.TV },
                )
                ChipRow(CreditFilter.entries, filter, { "${it.label} (${counts[it]})" }, { filter = it })
            }
        }

        items(credits, key = { it.card.key }) { credit ->
            val my = library[credit.card.key]
            PosterCard(
                title = credit.card.title,
                posterPath = credit.card.posterPath,
                subtitle = listOfNotNull(credit.date?.take(4)?.ifBlank { null } ?: tr("скоро", "soon", "скоро"), credit.role).joinToString(" · "),
                tmdbRating = credit.card.rating,
                myRating = my?.score,
                onClick = { onOpen(credit.card) },
                customPoster = my?.customPoster,
            )
        }
    }
}

private fun dateFormat() = DateTimeFormatter.ofPattern(tr("d MMMM yyyy", "MMMM d, yyyy", "d MMMM yyyy"), I18n.locale)

private fun departmentLabel(d: String?) = when (d) {
    "Acting" -> tr("Акторська гра", "Acting", "Актёрская игра")
    "Directing" -> tr("Режисура", "Directing", "Режиссура")
    "Writing" -> tr("Сценарії", "Writing", "Сценарии")
    "Production" -> tr("Продюсування", "Production", "Продюсирование")
    "Sound" -> tr("Музика і звук", "Music and sound", "Музыка и звук")
    "Camera" -> tr("Операторська робота", "Camera", "Операторская работа")
    else -> d
}

@Composable
private fun PersonHeader(p: PersonDetails) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp)) {
        Row {
            Box(
                Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (p.profilePath != null) {
                    AsyncImage(TmdbImages.profile(p.profilePath, "h632"), p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                departmentLabel(p.knownFor)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                val birth = p.birthday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val death = p.deathday?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (birth != null) {
                    Spacer(Modifier.height(8.dp))
                    val age = Period.between(birth, death ?: LocalDate.now()).years
                    val ageText = " ($age ${plural(age, "рік|роки|років", "year|years", "год|года|лет")})"
                    Text(
                        tr("Народження", "Born", "Рождение") + ": ${dateFormat().format(birth)}" + if (death == null) ageText else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (death != null) {
                    val years = birth?.let {
                        val n = Period.between(it, death).years
                        " ($n ${plural(n, "рік|роки|років", "year|years", "год|года|лет")})"
                    }.orEmpty()
                    Text(tr("Смерть", "Died", "Смерть") + ": ${dateFormat().format(death)}$years", style = MaterialTheme.typography.bodySmall)
                }
                p.placeOfBirth?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        p.biography?.takeIf { it.isNotBlank() }?.let { bio ->
            Spacer(Modifier.height(16.dp))
            Text(tr("Біографія", "Biography", "Биография"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(),
            )
            if (bio.length > 300) {
                Text(
                    if (expanded) tr("Згорнути", "Show less", "Свернуть") else tr("Читати далі", "Read more", "Читать далее"),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp).clickable { expanded = !expanded },
                )
            }
        }
    }
}
