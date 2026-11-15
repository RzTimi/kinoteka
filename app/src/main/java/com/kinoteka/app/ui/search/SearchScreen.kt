package com.kinoteka.app.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.friendlyMessage
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.EmptyBox
import com.kinoteka.app.ui.components.ErrorBox
import com.kinoteka.app.ui.components.LoadingBox
import com.kinoteka.app.ui.components.MediaGrid
import com.kinoteka.app.ui.components.SectionChips
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val section: Section? = null,
    val results: List<MediaCard> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    /** Фильтр по разделу делаем у себя: TMDB ищет сразу по всему. */
    val visible: List<MediaCard> get() = if (section == null) results else results.filter { it.section == section }
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repo: MediaRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        _state.map { it.query.trim() }
            .distinctUntilChanged()
            .debounce(400)
            .onEach { search(it) }
            .launchIn(viewModelScope)
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setSection(s: Section?) = _state.update { it.copy(section = s) }

    fun retry() = search(_state.value.query.trim())

    private fun search(q: String) {
        job?.cancel()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList(), page = 0, hasMore = false, loading = false, error = null) }
            return
        }
        _state.update { it.copy(loading = true, error = null, results = emptyList()) }
        job = viewModelScope.launch { load(q, 1) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { load(s.query.trim(), s.page + 1) }
    }

    private suspend fun load(q: String, page: Int) {
        runCatching { repo.search(q, page) }
            .onSuccess { r ->
                _state.update { st ->
                    val known = st.results.mapTo(HashSet()) { it.key }
                    st.copy(
                        results = st.results + r.items.filter { it.key !in known },
                        page = r.page, hasMore = r.hasMore, loading = false, loadingMore = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(loading = false, loadingMore = false, error = e.friendlyMessage()) }
            }
    }
}

@Composable
fun SearchScreen(onItem: (MediaCard) -> Unit) {
    val repo = LocalContainer.current.repository
    val vm: SearchViewModel = viewModel { SearchViewModel(repo) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Фільм, серіал, дорама…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, "Очистити") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                SectionChips(selected = state.section, onSelect = vm::setSection, showAll = true)
                Spacer(Modifier.height(4.dp))
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            state.query.trim().length < 2 -> EmptyBox("Введіть назву, щоб знайти фільм або серіал", modifier)
            state.loading -> LoadingBox(modifier)
            state.error != null && state.results.isEmpty() -> ErrorBox(state.error!!, vm::retry, modifier)
            state.visible.isEmpty() && !state.hasMore -> EmptyBox("Нічого не знайдено", modifier)
            else -> MediaGrid(
                items = state.visible,
                onItem = onItem,
                loadingMore = state.loadingMore,
                hasMore = state.hasMore,
                onLoadMore = vm::loadMore,
                modifier = modifier,
                showType = true,
            )
        }
    }
}
