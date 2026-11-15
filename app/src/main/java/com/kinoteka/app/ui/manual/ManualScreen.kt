package com.kinoteka.app.ui.manual

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.data.library.formatScore
import com.kinoteka.app.data.mediaKey
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.components.ChipRow
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.PosterImage
import com.kinoteka.app.ui.details.CollectionsRow
import com.kinoteka.app.ui.details.CropDialog
import com.kinoteka.app.ui.details.SnakeRating
import kotlinx.coroutines.launch

/**
 * Фильм или сериал, которого нет в TMDB: форма добавления/редактирования
 * и просмотр с оценкой-змейкой, статусами, заметкой и подборками.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualScreen(key: String?, onBack: () -> Unit) {
    val container = LocalContainer.current
    val library = container.library
    val entry = key?.let { LocalLibrary.current[it] }
    var editing by rememberSaveable { mutableStateOf(key == null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (key == null) tr("Додати фільм вручну", "Add a movie manually", "Добавить фильм вручную") else if (editing) tr("Редагування", "Editing", "Редактирование") else entry?.displayTitle.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) } },
                actions = {
                    if (entry != null && !editing) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, tr("Редагувати", "Edit", "Редактировать")) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Видалити", "Delete", "Удалить")) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (editing) {
                    ManualForm(entry) { saved ->
                        library.update(saved.key, create = { saved }) { old ->
                            old.copy(
                                title = saved.title, year = saved.year, section = saved.section,
                                overview = saved.overview, customPoster = saved.customPoster ?: old.customPoster,
                            )
                        }
                        if (key == null) onBack() else editing = false
                    }
                } else if (entry != null) {
                    ManualView(entry)
                }
            }
        }
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Видалити «${entry.displayTitle}»?", "Delete “${entry.displayTitle}”?", "Удалить «${entry.displayTitle}»?")) },
            text = { Text(tr("Фільм буде видалено з бібліотеки разом з оцінкою і нотаткою.", "The movie will be removed from your library along with your rating and note.", "Фильм будет удалён из библиотеки вместе с оценкой и заметкой.")) },
            confirmButton = {
                TextButton(onClick = {
                    container.posters.deleteIfLocal(entry.customPoster)
                    library.remove(entry.key)
                    confirmDelete = false
                    onBack()
                }) { Text(tr("Видалити", "Delete", "Удалить")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Скасувати", "Cancel", "Отмена")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualForm(existing: LibraryEntry?, onSave: (LibraryEntry) -> Unit) {
    val posters = LocalContainer.current.posters
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(existing?.type ?: MediaType.MOVIE) }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(existing?.year.orEmpty()) }
    var sectionName by rememberSaveable { mutableStateOf(existing?.section ?: Section.MOVIES.name) }
    var overview by rememberSaveable { mutableStateOf(existing?.overview.orEmpty()) }
    var poster by rememberSaveable { mutableStateOf(existing?.customPoster) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Ключ нового фильма: отрицательный id, чтобы не пересечься с TMDB.
    val newId = remember { -(System.currentTimeMillis() % 1_000_000_000).toInt() }
    val key = existing?.key ?: mediaKey(type, newId)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { cropBitmap = posters.decode(uri) }
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(120.dp).clip(RoundedCornerShape(10.dp)).clickable {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            PosterImage(null, if (poster == null) tr("Натисніть, щоб додати постер", "Tap to add a poster", "Нажмите, чтобы добавить постер") else title, Modifier.fillMaxWidth(), custom = poster)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing == null) {
                Text(tr("Тип", "Type", "Тип"), style = MaterialTheme.typography.labelLarge)
                ChipRow(listOf(MediaType.MOVIE, MediaType.TV), type, { if (it == MediaType.MOVIE) tr("Фільм", "Movie", "Фильм") else tr("Серіал", "TV Show", "Сериал") }, { type = it })
            }
            OutlinedTextField(title, { title = it }, label = { Text(tr("Назва", "Title", "Название")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                year, { v -> year = v.filter { it.isDigit() }.take(4) }, label = { Text(tr("Рік", "Year", "Год")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp),
            )
        }
    }
    Text(tr("Розділ", "Section", "Раздел"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Section.entries.filter { it.isBase }.forEach { s ->
            FilterChip(selected = s.name == sectionName, onClick = { sectionName = s.name }, label = { Text(s.title) })
        }
    }
    OutlinedTextField(overview, { overview = it }, label = { Text(tr("Опис (необов'язково)", "Description (optional)", "Описание (необязательно)")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            onSave(
                LibraryEntry(
                    mediaType = type.path,
                    tmdbId = existing?.tmdbId ?: newId,
                    title = title.trim(),
                    year = year.ifBlank { null },
                    section = sectionName,
                    overview = overview.trim().ifBlank { null },
                    customPoster = poster,
                    manual = true,
                    status = existing?.status ?: WatchStatus.WANT,
                )
            )
        },
        enabled = title.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (existing == null) tr("Додати в бібліотеку", "Add to library", "Добавить в библиотеку") else tr("Зберегти", "Save", "Сохранить")) }

    cropBitmap?.let { bmp ->
        CropDialog(bitmap = bmp, onCancel = { cropBitmap = null }, onDone = { rect ->
            cropBitmap = null
            scope.launch {
                val old = poster
                poster = posters.saveCropped(bmp, rect, key)
                if (old != existing?.customPoster) posters.deleteIfLocal(old)
            }
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualView(e: LibraryEntry) {
    val library = LocalContainer.current.library
    fun edit(change: (LibraryEntry) -> LibraryEntry) = library.update(e.key, create = { e }) { change(it) }

    Row {
        PosterImage(null, e.displayTitle, Modifier.width(120.dp), custom = e.customPoster)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(e.displayTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(e.sectionEnum.title, e.year, tr("додано вручну", "added manually", "добавлено вручную")).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Моя оцінка", "My rating", "Моя оценка"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(e.score?.let { "${formatScore(it)} / 10" } ?: tr("не оцінено", "not rated", "не оценено"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            SnakeRating(
                rating = e.score,
                onRate = { v ->
                    edit { it.withScore(v).copy(status = if (it.status == null || it.status == WatchStatus.WANT) WatchStatus.WATCHED else it.status, watchedAt = it.watchedAt ?: System.currentTimeMillis()) }
                },
                onClear = { edit { it.withScore(null) } },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchStatus.entries.filter { !it.tvOnly || e.type == MediaType.TV }.forEach { st ->
                    FilterChip(
                        selected = e.status == st,
                        onClick = { edit { it.copy(status = if (it.status == st) null else st) } },
                        label = { Text(st.label) },
                    )
                }
                FilterChip(selected = e.favorite, onClick = { edit { it.copy(favorite = !it.favorite) } }, label = { Text(tr("❤ Улюблене", "❤ Favorite", "❤ Избранное")) })
            }
            Spacer(Modifier.height(8.dp))
            CollectionsRow(
                current = e.collections,
                onToggle = { n -> edit { it.copy(collections = if (n in it.collections) it.collections - n else it.collections + n) } },
                onCreate = { n -> library.createCollection(n); edit { it.copy(collections = it.collections + n.trim()) } },
            )
        }
    }
    e.overview?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    var note by remember(e.key) { mutableStateOf(e.note) }
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text(tr("Моя нотатка", "My note", "Моя заметка")) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    if (note != e.note) {
        TextButton(onClick = { edit { it.copy(note = note.trim()) } }) { Text(tr("Зберегти нотатку", "Save note", "Сохранить заметку")) }
    }
}
