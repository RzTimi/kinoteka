package com.kinoteka.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.library.CastRef
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr

/** Актёр в вашей библиотеке и сколько у вас фильмов с ним. */
data class LibraryActor(val id: Int, val name: String, val count: Int)

private fun LibraryActor.label() = if (count > 1) "$name · $count" else name

/**
 * Ряд актёров в «Мої оцінки»: «Усі актори», «Вибрати актора…»,
 * выбранный (если не закреплён) и закреплённые актёры.
 */
@Composable
fun ActorRow(
    selectedId: Int?,
    selectedName: String?,
    pinned: List<CastRef>,
    counts: Map<Int, Int>,
    onAll: () -> Unit,
    onChoose: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selectedId == null, onClick = onAll, label = { Text(tr("Усі актори", "All actors", "Все актёры")) })
        }
        item {
            FilterChip(
                selected = false,
                onClick = onChoose,
                label = { Text(tr("Вибрати актора…", "Choose actor…", "Выбрать актёра…")) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        // Выбран актёр, которого нет среди закреплённых, — показываем его, чтобы было видно фильтр.
        if (selectedId != null && pinned.none { it.id == selectedId }) {
            item(key = "selected") {
                FilterChip(
                    selected = true,
                    onClick = onChoose,
                    label = { Text("🎭 " + LibraryActor(selectedId, selectedName.orEmpty(), counts[selectedId] ?: 0).label()) },
                    trailingIcon = {
                        IconButton(onClick = { onSelect(null) }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, tr("Прибрати", "Remove", "Убрать"), Modifier.size(16.dp))
                        }
                    },
                )
            }
        }
        pinned.forEach { a ->
            item(key = "pin-${a.id}") {
                val actor = LibraryActor(a.id, a.name, counts[a.id] ?: 0)
                FilterChip(
                    selected = selectedId == a.id,
                    onClick = { onSelect(if (selectedId == a.id) null else a.id) },
                    label = { Text("🎭 " + actor.label()) },
                )
            }
        }
    }
}

/** Окно выбора актёра: поиск, все актёры вашей библиотеки, 📌 — закрепить в ряду. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActorPickerSheet(
    actors: List<LibraryActor>,
    selectedId: Int?,
    pinnedIds: Set<Int>,
    onSelect: (Int) -> Unit,
    onTogglePin: (CastRef) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val found = actors.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val shown = found.take(MAX_SHOWN)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr("Актори", "Actors", "Актёры"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${actors.size} ${plural(actors.size, "актор|актори|акторів", "actor|actors", "актёр|актёра|актёров")} " +
                    tr("у ваших фільмах. Натисніть — покажемо їхні фільми, 📌 — закріпити в ряду.",
                        "in your titles. Tap to show their titles, 📌 to pin to the row.",
                        "в ваших фильмах. Нажмите — покажем их фильмы, 📌 — закрепить в ряду."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr("Знайти актора", "Find an actor", "Найти актёра")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, tr("Очистити", "Clear", "Очистить")) }
                },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            )
            if (shown.isEmpty()) {
                Text(
                    tr("Нікого не знайдено", "Nobody found", "Никого не найдено"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { a ->
                    val isPinned = a.id in pinnedIds
                    FilterChip(
                        selected = a.id == selectedId,
                        onClick = { onSelect(a.id) },
                        label = { Text("🎭 " + a.label()) },
                        trailingIcon = {
                            IconButton(onClick = { onTogglePin(CastRef(a.id, a.name)) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    if (isPinned) tr("Відкріпити", "Unpin", "Открепить") else tr("Закріпити", "Pin", "Закрепить"),
                                    Modifier.size(18.dp),
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
            if (found.size > shown.size) {
                Text(
                    tr("Показано перших $MAX_SHOWN — уточніть пошук", "Showing the first $MAX_SHOWN — refine the search", "Показаны первые $MAX_SHOWN — уточните поиск"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_SHOWN = 200
