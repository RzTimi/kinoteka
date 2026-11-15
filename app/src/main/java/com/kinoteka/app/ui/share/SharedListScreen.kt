package com.kinoteka.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.LibraryStore
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.data.library.formatScore
import com.kinoteka.app.data.mediaKey
import com.kinoteka.app.data.share.SharedItem
import com.kinoteka.app.data.share.SharedPayload
import com.kinoteka.app.data.share.ShareLink
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.EmptyBox
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.PosterCard

/** Подборка, которую прислали ссылкой: посмотреть и одним нажатием добавить себе. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedListScreen(data: String, onBack: () -> Unit, onOpen: (MediaCard) -> Unit) {
    val payload = remember(data) { ShareLink.decode(data) }
    val container = LocalContainer.current
    val library = LocalLibrary.current
    var added by rememberSaveable(data) { mutableStateOf<Int?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr("Вам надіслали", "Shared with you", "Вам прислали"), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        if (payload == null) {
            EmptyBox(tr("Посилання пошкоджене", "The link is broken", "Ссылка повреждена"), modifier)
            return@Scaffold
        }
        val name = payload.title ?: tr("Підбірка", "Collection", "Подборка")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📁 $name", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val n = payload.items.size
                    Text(
                        "$n ${plural(n, "фільм|фільми|фільмів", "title|titles", "фильм|фильма|фильмов")}" +
                            tr(" · 🐍 — оцінки відправника", " · 🐍 — sender's ratings", " · 🐍 — оценки отправителя"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val done = added
                    if (done == null) {
                        Button(onClick = { added = importShared(container.library, name, payload) }) {
                            Icon(Icons.Default.LibraryAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Додати собі в «$name»", "Add to my “$name”", "Добавить себе в «$name»"))
                        }
                        Text(
                            tr(
                                "Фільми з'являться в «Мої оцінки» зі статусом «Хочу подивитись» в окремій підбірці.",
                                "Titles will appear in “My ratings” as “Want to watch”, in a separate collection.",
                                "Фильмы появятся в «Мои оценки» со статусом «Хочу посмотреть» в отдельной подборке.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("Додано в підбірку «$name»", "Added to the “$name” collection", "Добавлено в подборку «$name»") +
                                    if (done < payload.items.size) tr(
                                        " (нових: $done)", " ($done new)", " (новых: $done)",
                                    ) else "",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(payload.items, key = { "${it.kind}:${it.id}" }) { it ->
                val my = library[mediaKey(it.type, it.id)]
                PosterCard(
                    title = my?.displayTitle ?: it.title,
                    posterPath = it.poster,
                    subtitle = listOfNotNull(it.year, it.score?.let { s -> "🐍 ${formatScore(s)}" }).joinToString(" · "),
                    tmdbRating = null,
                    myRating = my?.score,
                    onClick = { onOpen(it.toCard()) },
                    customPoster = my?.customPoster,
                )
            }
        }
    }
}

private fun SharedItem.section(): Section =
    Section.fromName(section) ?: if (type == MediaType.TV) Section.SERIES else Section.MOVIES

private fun SharedItem.toCard() = MediaCard(id, type, title, poster, year, 0.0, section())

/**
 * Добавить присланное себе: подборка с тем же названием, статус «Хочу подивитись»
 * (если фильм уже есть — статус и оценка не меняются, он просто попадает в подборку).
 * Возвращает, сколько фильмов было новых.
 */
private fun importShared(library: LibraryStore, name: String, payload: SharedPayload): Int {
    library.createCollection(name)
    val before = library.entries.value
    var fresh = 0
    payload.items.forEach { it ->
        val key = mediaKey(it.type, it.id)
        if (key !in before) fresh++
        library.update(key, create = {
            LibraryEntry(
                mediaType = it.type.path,
                tmdbId = it.id,
                title = it.title,
                posterPath = it.poster,
                year = it.year,
                section = it.section().name,
            )
        }) { e ->
            e.copy(
                status = e.status ?: WatchStatus.WANT,
                collections = (e.collections + name).distinct(),
            )
        }
    }
    return fresh
}
