package com.kinoteka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.PosterStorage
import androidx.compose.ui.platform.LocalContext
import com.kinoteka.app.ui.theme.StarColor
import java.util.Locale

/** Ваша библиотека оценок, доступна любому экрану (для значков «моя оценка»). */
val LocalLibrary = compositionLocalOf<Map<String, LibraryEntry>> { emptyMap() }

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}

fun formatRating(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
fun SectionChips(
    selected: Section?,
    onSelect: (Section?) -> Unit,
    modifier: Modifier = Modifier,
    showAll: Boolean = false,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showAll) item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Усі") })
        }
        items(Section.entries) { s ->
            FilterChip(selected = selected == s, onClick = { onSelect(s) }, label = { Text(s.title) })
        }
    }
}

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

/** Постер: оригинальный с TMDB или ваш собственный ([custom]), если он задан. */
@Composable
fun PosterImage(
    path: String?,
    title: String,
    modifier: Modifier = Modifier,
    size: String = "w342",
    custom: String? = null,
) {
    val filesDir = LocalContext.current.filesDir
    val model = remember(path, custom, size) { PosterStorage.model(filesDir, custom, path, size) }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    posterPath: String?,
    subtitle: String?,
    tmdbRating: Double?,
    myRating: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    customPoster: String? = null,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            PosterImage(posterPath, title, Modifier.fillMaxWidth(), custom = customPoster)
            if (tmdbRating != null && tmdbRating > 0) {
                Badge(
                    text = formatRating(tmdbRating),
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    background = Color.Black.copy(alpha = 0.65f),
                    content = Color.White,
                )
            }
            if (myRating != null) {
                Badge(
                    text = "$myRating",
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    background = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier, background: Color, content: Color) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = if (content == Color.White) StarColor else content, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MediaCardItem(card: MediaCard, onClick: (MediaCard) -> Unit, modifier: Modifier = Modifier, showType: Boolean = false) {
    val my = LocalLibrary.current[card.key]
    val subtitle = listOfNotNull(card.year, if (showType) card.section.title else null).joinToString(" · ")
    PosterCard(
        title = card.title,
        posterPath = card.posterPath,
        subtitle = subtitle,
        tmdbRating = card.rating,
        myRating = my?.rating,
        onClick = { onClick(card) },
        modifier = modifier,
        customPoster = my?.customPoster,
    )
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaCard>,
    onItem: (MediaCard) -> Unit,
    onMore: (() -> Unit)?,
    itemWidth: Dp = 116.dp,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onMore != null) {
                TextButton(onClick = onMore) {
                    Text("Усі")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.key }) { card ->
                MediaCardItem(card, onItem, Modifier.width(itemWidth))
            }
        }
    }
}

/** Сетка постеров с подгрузкой следующей страницы при прокрутке вниз. */
@Composable
fun MediaGrid(
    items: List<MediaCard>,
    onItem: (MediaCard) -> Unit,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    showType: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, hasMore, loadingMore, items.size) {
        if (nearEnd && hasMore && !loadingMore) onLoadMore()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(items, key = { it.key }) { card -> MediaCardItem(card, onItem, showType = showType) }
        if (loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторити") }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
