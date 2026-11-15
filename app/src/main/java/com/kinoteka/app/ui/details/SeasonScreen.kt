package com.kinoteka.app.ui.details

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.data.tmdb.Episode
import com.kinoteka.app.data.tmdb.SeasonDetails
import com.kinoteka.app.data.tmdb.TmdbImages
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.UiState
import com.kinoteka.app.data.mediaKey
import com.kinoteka.app.data.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SeasonViewModel(private val repo: MediaRepository, private val tvId: Int, private val season: Int) : ViewModel() {
    private val _state = MutableStateFlow<UiState<SeasonDetails>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repo.season(tvId, season) }
                .fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
        }
    }
}

/**
 * Серии сезона с галочками. Отметки пишутся через [details] (ViewModel карточки сериала),
 * чтобы статус «Дивлюсь»/«Переглянуто» обновлялся сам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonScreen(tvId: Int, season: Int, showTitle: String, details: DetailsViewModel, onBack: () -> Unit) {
    val repo = LocalContainer.current.repository
    val vm: SeasonViewModel = viewModel(key = "season_${tvId}_$season") { SeasonViewModel(repo, tvId, season) }
    val state by vm.state.collectAsStateWithLifecycle()
    val entry = LocalLibrary.current[mediaKey(MediaType.TV, tvId)]
    val watched = entry?.episodes?.get(season.toString()).orEmpty().toSet()
    val today = LocalDate.now().toString()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (season == 0) "Спецвипуски" else "Сезон $season")
                        Text(showTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    val s = state
                    if (s is UiState.Success) {
                        // Отмечаем только вышедшие серии.
                        val aired = s.data.episodes.filter { (it.airDate ?: "9999") <= today }.size
                        IconButton(onClick = { details.toggleSeason(season, aired) }) {
                            Icon(Icons.Default.DoneAll, "Відмітити весь сезон")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = state) {
            UiState.Loading -> LoadingBox(modifier)
            is UiState.Error -> ErrorBox(s.message, vm::load, modifier)
            is UiState.Success -> {
                val total = s.data.episodes.size
                LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text("Переглянуто ${watched.size} з $total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (total == 0) 0f else watched.size.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                    items(s.data.episodes, key = { it.number }) { ep ->
                        val aired = (ep.airDate ?: "9999") <= today
                        EpisodeRow(season, ep, ep.number in watched, aired) { details.toggleEpisode(season, ep.number) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(season: Int, ep: Episode, watched: Boolean, aired: Boolean, onToggle: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (aired) 1f else 0.55f),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (ep.stillPath != null) {
                AsyncImage(TmdbImages.still(ep.stillPath), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Tv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).animateContentSize()) {
            Text(
                "S${season}E${ep.number} · ${ep.name.orEmpty()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    ep.airDate?.let { if (aired) it else "вийде $it" },
                    ep.runtime?.takeIf { it > 0 }?.let { "$it хв" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ep.overview.isNullOrBlank()) {
                Text(
                    ep.overview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Checkbox(checked = watched, onCheckedChange = { onToggle() }, enabled = aired, modifier = Modifier.size(40.dp))
    }
}
