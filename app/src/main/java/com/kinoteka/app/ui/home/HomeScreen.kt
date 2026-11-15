package com.kinoteka.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.data.Feed
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.EmptyBox
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.MediaRow
import com.kinoteka.app.ui.components.SectionChips
import com.kinoteka.app.ui.components.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedRow(val feed: Feed, val items: List<MediaCard>)

class HomeViewModel(private val repo: MediaRepository, private val section: Section) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<FeedRow>>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val results = section.homeFeeds().map { feed ->
                async { runCatching { FeedRow(feed, repo.feed(section, feed)) } }
            }.awaitAll()
            val rows = results.mapNotNull { it.getOrNull() }.filter { it.items.isNotEmpty() }
            _state.value = when {
                rows.isNotEmpty() -> UiState.Success(rows)
                results.any { it.isFailure } ->
                    UiState.Error(results.first { it.isFailure }.exceptionOrNull()!!.friendlyMessage())
                else -> UiState.Success(emptyList())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    section: Section,
    onSection: (Section) -> Unit,
    onItem: (MediaCard) -> Unit,
    onMore: (Feed) -> Unit,
    onAbout: () -> Unit,
) {
    val repo = LocalContainer.current.repository
    // Отдельная ViewModel на каждый раздел: при переключении назад всё уже загружено.
    val vm: HomeViewModel = viewModel(key = "home_${section.name}") { HomeViewModel(repo, section) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Кінотека", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onAbout) { Icon(Icons.Outlined.Info, "Про застосунок") }
                    },
                )
                SectionChips(selected = section, onSelect = { it?.let(onSection) })
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = state) {
            UiState.Loading -> LoadingBox(modifier)
            is UiState.Error -> ErrorBox(s.message, vm::load, modifier)
            is UiState.Success -> if (s.data.isEmpty()) {
                EmptyBox("Нічого не знайдено", modifier)
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(s.data, key = { it.feed.title }) { row ->
                        MediaRow(
                            title = row.feed.title,
                            items = row.items,
                            onItem = onItem,
                            onMore = { onMore(row.feed) },
                        )
                    }
                }
            }
        }
    }
}
