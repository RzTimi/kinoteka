package com.kinoteka.app.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.SortOption
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.ui.BrowseRoute
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.RouteParams
import com.kinoteka.app.ui.components.ChipRow
import com.kinoteka.app.ui.components.EmptyBox
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.MediaGrid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseState(
    val items: List<MediaCard> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val sort: SortOption = SortOption.POPULAR,
    /** Код страны для дорам, null = все. */
    val country: String? = null,
)

class BrowseViewModel(
    private val repo: MediaRepository,
    private val section: Section,
    private val type: MediaType,
    private val params: Map<String, String>,
    private val sortable: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun setSort(sort: SortOption) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun setCountry(code: String?) {
        if (code == _state.value.country) return
        _state.update { it.copy(country = code) }
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.update { it.copy(items = emptyList(), page = 0, hasMore = true, loading = true, error = null) }
        job = viewModelScope.launch { loadPage(1) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { loadPage(s.page + 1) }
    }

    private suspend fun loadPage(page: Int) {
        val s = _state.value
        val extra = buildMap {
            putAll(params)
            if (sortable) putAll(s.sort.params(type))
            s.country?.let { put("with_origin_country", it) }
        }
        runCatching { repo.discover(section, type, extra, page) }
            .onSuccess { r ->
                _state.update { st ->
                    val known = st.items.mapTo(HashSet()) { it.key }
                    st.copy(
                        items = st.items + r.items.filter { it.key !in known },
                        page = r.page,
                        hasMore = r.hasMore,
                        loading = false,
                        loadingMore = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    if (it.items.isEmpty()) it.copy(loading = false, loadingMore = false, error = e.friendlyMessage())
                    else it.copy(loadingMore = false, hasMore = false)
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(route: BrowseRoute, onBack: () -> Unit, onItem: (MediaCard) -> Unit) {
    val repo = LocalContainer.current.repository
    val section = Section.fromName(route.section) ?: Section.MOVIES
    val type = MediaType.fromPath(route.type) ?: MediaType.MOVIE
    val vm: BrowseViewModel = viewModel {
        BrowseViewModel(repo, section, type, RouteParams.decode(route.paramsJson), route.sortable)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val showCountries = section.countries.isNotEmpty() && type == MediaType.TV

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(route.title, maxLines = 1)
                            Text(section.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    },
                )
                if (route.sortable) {
                    ChipRow(SortOption.entries, state.sort, { it.label }, vm::setSort)
                }
                if (showCountries) {
                    Spacer(Modifier.height(4.dp))
                    val options: List<Pair<String?, String>> = listOf<Pair<String?, String>>(null to "Усі країни") + section.countries
                    ChipRow(options, options.first { it.first == state.country }, { it.second }, { vm.setCountry(it.first) })
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.loading -> LoadingBox(modifier)
            state.error != null -> ErrorBox(state.error!!, vm::reload, modifier)
            state.items.isEmpty() && !state.hasMore -> EmptyBox("Нічого не знайдено", modifier)
            else -> MediaGrid(
                items = state.items,
                onItem = onItem,
                loadingMore = state.loadingMore,
                hasMore = state.hasMore,
                onLoadMore = vm::loadMore,
                modifier = modifier,
            )
        }
    }
}
