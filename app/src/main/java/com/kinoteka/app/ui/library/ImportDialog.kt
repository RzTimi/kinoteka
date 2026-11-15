package com.kinoteka.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.ui.LocalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Строка из CSV: название, год, оценка 1–10 (или null), дата просмотра. */
data class ImportRow(val title: String, val year: Int?, val rating: Int?, val watchedAt: Long?)

/**
 * Разбор CSV: Letterboxd (ratings.csv / watched.csv / diary.csv) или любой CSV
 * с колонками «Назва/Name/Title», «Рік/Year», «Оцінка/Rating».
 */
fun parseImportCsv(text: String): List<ImportRow> {
    val lines = parseCsv(text.removePrefix("﻿"))
    if (lines.size < 2) return emptyList()
    val header = lines.first().map { it.trim().lowercase() }
    fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h == it || h.contains(it) } }
    val nameCol = col("name", "title", "назва", "название", "фільм", "фильм").takeIf { it >= 0 } ?: 0
    val yearCol = col("year", "рік", "год")
    val ratingCol = col("rating", "оцінка", "оценка", "rate")
    val dateCol = col("watched date", "date", "дата")
    val letterboxd = header.any { it.contains("letterboxd") }
    val rows = lines.drop(1).filter { it.size > nameCol && it[nameCol].isNotBlank() }
    // Letterboxd ставит оценки 0.5–5, а у нас 1–10.
    val rawRatings = rows.mapNotNull { r -> r.getOrNull(ratingCol)?.replace(',', '.')?.toDoubleOrNull() }
    val fiveScale = letterboxd || (rawRatings.isNotEmpty() && rawRatings.max() <= 5.0)
    return rows.map { r ->
        val rating = r.getOrNull(ratingCol)?.replace(',', '.')?.toDoubleOrNull()?.let { v ->
            (if (fiveScale) v * 2 else v).roundToInt().coerceIn(1, 10)
        }
        val date = r.getOrNull(dateCol)?.trim()?.take(10)?.let { d ->
            runCatching { LocalDate.parse(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        }
        ImportRow(r[nameCol].trim(), r.getOrNull(yearCol)?.trim()?.take(4)?.toIntOrNull(), rating, date)
    }
}

/** Простой парсер CSV с кавычками; разделитель — запятая или точка с запятой. */
private fun parseCsv(text: String): List<List<String>> {
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
    val result = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == sep -> { row.add(cell.toString()); cell.clear() }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                row.add(cell.toString()); cell.clear()
                if (row.any { it.isNotBlank() }) result.add(row)
                row = mutableListOf()
            }
            else -> cell.append(c)
        }
        i++
    }
    row.add(cell.toString())
    if (row.any { it.isNotBlank() }) result.add(row)
    return result
}

@Composable
fun ImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        running = true
        scope.launch {
            val rows = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                }.map(::parseImportCsv).getOrDefault(emptyList())
            }
            total = rows.size
            val existing = container.library.entries.value
            val added = mutableListOf<LibraryEntry>()
            val notFound = mutableListOf<String>()
            rows.forEach { row ->
                val found = runCatching { container.repository.findMovie(row.title, row.year) }.getOrNull()
                if (found == null) {
                    notFound += row.title
                } else {
                    val entry = LibraryEntry(
                        mediaType = MediaType.MOVIE.path,
                        tmdbId = found.id,
                        title = found.title ?: row.title,
                        posterPath = found.posterPath,
                        year = (found.releaseDate ?: "").take(4).ifBlank { row.year?.toString() },
                        section = Section.classify(found, MediaType.MOVIE).name,
                        rating = row.rating,
                        status = WatchStatus.WATCHED,
                        watchedAt = row.watchedAt ?: System.currentTimeMillis(),
                    )
                    if (entry.key !in existing && added.none { it.key == entry.key }) added += entry
                }
                done++
            }
            container.library.addAll(added)
            result = buildString {
                append("Додано: ${added.size} з ${rows.size}.")
                val skipped = rows.size - added.size - notFound.size
                if (skipped > 0) append("\nУже були в бібліотеці: $skipped.")
                if (notFound.isNotEmpty()) {
                    append("\nНе знайдено (${notFound.size}): ")
                    append(notFound.take(20).joinToString(", "))
                    if (notFound.size > 20) append("…")
                }
                if (rows.isEmpty()) append("\nУ файлі не знайшлося рядків. Перевірте, що це CSV з колонкою назви.")
            }
            running = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Імпорт оцінок") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when {
                    running -> {
                        Text("Шукаю фільми в TMDB: $done з $total…")
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    }
                    result != null -> Text(result!!)
                    else -> {
                        Text("Letterboxd", style = MaterialTheme.typography.titleSmall)
                        Text("letterboxd.com → Settings → Import & Export → Export your data. У архіві оберіть ratings.csv (з оцінками) або watched.csv.")
                        Spacer(Modifier.height(10.dp))
                        Text("Кінопошук та інші", style = MaterialTheme.typography.titleSmall)
                        Text("CSV-файл з колонками «Назва», «Рік», «Оцінка» (1–10). Такий файл можна зробити в Excel або Google Таблицях.")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Фільми знаходяться в TMDB за назвою і роком. Оцінки з Letterboxd (0,5–5) перераховуються у 1–10.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                running -> {}
                result != null -> TextButton(onClick = onDismiss) { Text("Готово") }
                else -> Button(onClick = { picker.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel", "*/*")) }) {
                    Text("Обрати файл CSV")
                }
            }
        },
        dismissButton = { if (!running && result == null) TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
