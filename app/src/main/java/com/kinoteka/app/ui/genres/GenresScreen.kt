package com.kinoteka.app.ui.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.data.tmdb.Genre
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ChipRow
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.SectionChips
import com.kinoteka.app.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GenresViewModel(
    private val repo: MediaRepository,
    private val section: Section,
    private val type: MediaType,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Genre>>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repo.genres(section, type) }
                .fold({ UiState.Success(it) }, { UiState.Error(it.friendlyMessage()) })
        }
    }
}

private fun typeLabel(section: Section, type: MediaType) = when (type) {
    MediaType.TV -> if (section == Section.CARTOONS) "Мультсеріали" else "Серіали"
    MediaType.MOVIE -> if (section == Section.CARTOONS) "Мультфільми" else "Фільми"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    section: Section,
    onSection: (Section) -> Unit,
    onOpen: (type: MediaType, title: String, params: Map<String, String>) -> Unit,
) {
    val repo = LocalContainer.current.repository
    var typeName by rememberSaveable(section) { mutableStateOf(section.types.first().path) }
    val type = MediaType.fromPath(typeName)?.takeIf { it in section.types } ?: section.types.first()
    val vm: GenresViewModel = viewModel(key = "genres_${section.name}_${type.path}") {
        GenresViewModel(repo, section, type)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(title = { Text("Жанри", fontWeight = FontWeight.Bold) })
                SectionChips(selected = section, onSelect = { it?.let(onSection) })
                if (section.types.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    ChipRow(
                        options = section.types,
                        selected = type,
                        label = { typeLabel(section, it) },
                        onSelect = { typeName = it.path },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = state) {
            UiState.Loading -> LoadingBox(modifier)
            is UiState.Error -> ErrorBox(s.message, vm::load, modifier)
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GenreTile("Усі ${typeLabel(section, type).lowercase()}", highlighted = true) {
                        onOpen(type, "${section.title}: усі", emptyMap())
                    }
                }
                items(s.data, key = { it.id }) { genre ->
                    GenreTile(genre.name.replaceFirstChar { it.uppercase() }) {
                        onOpen(type, genre.name.replaceFirstChar { it.uppercase() }, mapOf("with_genres" to genre.id.toString()))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreTile(title: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = if (highlighted) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}
