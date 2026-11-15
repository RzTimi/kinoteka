package com.kinoteka.app.data.library

import android.content.Context
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.mediaKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class WatchStatus(val label: String, val tvOnly: Boolean = false) {
    WANT("Хочу подивитись"),
    WATCHING("Дивлюсь", tvOnly = true),
    WATCHED("Переглянуто"),
    DROPPED("Кинув"),
}

@Serializable
data class LibraryEntry(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String? = null,
    val year: String? = null,
    val section: String,
    /** Ваша оценка 1..10 или null. */
    val rating: Int? = null,
    val status: WatchStatus? = null,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val type: MediaType get() = MediaType.fromPath(mediaType) ?: MediaType.MOVIE
    val sectionEnum: Section get() = Section.fromName(section) ?: Section.MOVIES
    val key: String get() = mediaKey(type, tmdbId)
    val isEmpty: Boolean get() = rating == null && status == null && note.isBlank()
}

@Serializable
data class LibraryFile(val version: Int = 1, val entries: List<LibraryEntry> = emptyList())

/**
 * Ваши оценки, статусы и заметки. Хранятся одним JSON-файлом на телефоне:
 * тот же формат потом уйдёт в data.json на Google Диске.
 */
class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _entries = MutableStateFlow<Map<String, LibraryEntry>>(emptyMap())
    val entries: StateFlow<Map<String, LibraryEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            val loaded = runCatching {
                if (file.exists()) json.decodeFromString<LibraryFile>(file.readText()).entries else emptyList()
            }.getOrElse { emptyList() }
            _entries.update { current -> loaded.associateBy { it.key } + current }
        }
    }

    /** Изменить запись (или создать). Пустые записи удаляются. */
    fun update(key: String, create: () -> LibraryEntry, change: (LibraryEntry) -> LibraryEntry) {
        _entries.update { map ->
            val updated = change(map[key] ?: create()).copy(updatedAt = System.currentTimeMillis())
            if (updated.isEmpty) map - key else map + (key to updated)
        }
        persist()
    }

    fun remove(key: String) {
        _entries.update { it - key }
        persist()
    }

    private fun persist() {
        val snapshot = _entries.value.values.sortedByDescending { it.updatedAt }
        scope.launch {
            writeMutex.withLock {
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(json.encodeToString(LibraryFile.serializer(), LibraryFile(entries = snapshot)))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }
}
