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
    /**
     * Свой постер: "tmdb:/путь.jpg" (другой постер с TMDB) или
     * "file:имя.jpg" (картинка из галереи, лежит в files/posters). null = оригинальный.
     */
    val customPoster: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val type: MediaType get() = MediaType.fromPath(mediaType) ?: MediaType.MOVIE
    val sectionEnum: Section get() = Section.fromName(section) ?: Section.MOVIES
    val key: String get() = mediaKey(type, tmdbId)
    val isEmpty: Boolean get() = rating == null && status == null && note.isBlank() && customPoster == null
}

@Serializable
data class LibraryFile(
    val version: Int = 1,
    val entries: List<LibraryEntry> = emptyList(),
    val deleted: Map<String, Long> = emptyMap(),
)

/**
 * Ваши оценки, статусы и заметки. Хранятся одним JSON-файлом на телефоне:
 * тот же формат потом уйдёт в data.json на Google Диске.
 */
class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _entries = MutableStateFlow<Map<String, LibraryEntry>>(emptyMap())
    val entries: StateFlow<Map<String, LibraryEntry>> = _entries.asStateFlow()

    /** Удалённые записи: ключ → когда удалили. Нужно, чтобы удалённое не вернулось с Google Диска. */
    private val deleted = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Растёт при каждом изменении, сделанном на этом телефоне (не синхронизацией). */
    private val _localVersion = MutableStateFlow(0L)
    val localVersion: StateFlow<Long> = _localVersion.asStateFlow()

    init {
        scope.launch {
            val loaded = runCatching {
                if (file.exists()) JSON.decodeFromString<LibraryFile>(file.readText()) else LibraryFile()
            }.getOrElse { LibraryFile() }
            _entries.update { current -> loaded.entries.associateBy { it.key } + current }
            deleted.update { loaded.deleted + it }
            _loaded.value = true
        }
    }

    /** Изменить запись (или создать). Пустые записи удаляются. */
    fun update(key: String, create: () -> LibraryEntry, change: (LibraryEntry) -> LibraryEntry) {
        val now = System.currentTimeMillis()
        var becameEmpty = false
        _entries.update { map ->
            val updated = change(map[key] ?: create()).copy(updatedAt = now)
            becameEmpty = updated.isEmpty
            if (updated.isEmpty) map - key else map + (key to updated)
        }
        deleted.update { if (becameEmpty) it + (key to now) else it - key }
        localChanged()
    }

    fun remove(key: String) {
        _entries.update { it - key }
        deleted.update { it + (key to System.currentTimeMillis()) }
        localChanged()
    }

    /** Всё содержимое для синхронизации. */
    fun snapshot(): LibraryFile = LibraryFile(
        entries = _entries.value.values.sortedByDescending { it.updatedAt },
        deleted = deleted.value,
    )

    /** Заменить содержимое результатом синхронизации (без повторного запуска синхронизации). */
    fun replaceFromSync(file: LibraryFile) {
        // Сливаем ещё раз с текущим состоянием: вдруг вы что-то изменили, пока шла синхронизация.
        val final = merge(file, snapshot())
        _entries.value = final.entries.associateBy { it.key }
        deleted.value = final.deleted
        persist()
    }

    private fun localChanged() {
        _localVersion.update { it + 1 }
        persist()
    }

    private fun persist() {
        val snapshot = snapshot()
        scope.launch {
            writeMutex.withLock {
                val tmp = File(file.parentFile, "library.json.tmp")
                tmp.writeText(JSON.encodeToString(LibraryFile.serializer(), snapshot))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

        private const val TOMBSTONE_TTL_MS = 180L * 24 * 60 * 60 * 1000

        /**
         * Слияние телефона и Диска: по каждому фильму побеждает более свежая версия;
         * удаление побеждает, если оно новее последнего изменения.
         */
        fun merge(a: LibraryFile, b: LibraryFile, now: Long = System.currentTimeMillis()): LibraryFile {
            val deleted = (a.deleted.keys + b.deleted.keys).associateWith { k -> maxOf(a.deleted[k] ?: 0L, b.deleted[k] ?: 0L) }
            val newest = (a.entries + b.entries).groupBy { it.key }.mapValues { (_, list) -> list.maxBy { it.updatedAt } }
            val alive = newest.values.filter { e -> (deleted[e.key] ?: 0L) < e.updatedAt }
            val aliveKeys = alive.mapTo(HashSet()) { it.key }
            val tombstones = deleted.filter { (k, t) -> k !in aliveKeys && now - t < TOMBSTONE_TTL_MS }
            return LibraryFile(entries = alive.sortedByDescending { it.updatedAt }, deleted = tombstones)
        }
    }
}
