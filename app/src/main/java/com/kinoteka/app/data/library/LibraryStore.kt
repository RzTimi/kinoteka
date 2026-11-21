package com.kinoteka.app.data.library

import android.content.Context
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.mediaKey
import com.kinoteka.app.i18n.tr
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

/** Статусы как у MyAnimeList / Shikimori. Порядок = порядок кнопок. */
enum class WatchStatus(
    private val uk: String,
    private val en: String,
    private val ru: String,
    val tvOnly: Boolean = false,
) {
    WANT("Хочу подивитись", "Want to watch", "Хочу посмотреть"),
    WATCHING("Дивлюсь", "Watching", "Смотрю", tvOnly = true),
    REWATCHING("Переглядаю", "Rewatching", "Пересматриваю"),
    WATCHED("Переглянуто", "Watched", "Просмотрено"),
    ON_HOLD("Відкладено", "On hold", "Отложено", tvOnly = true),
    DROPPED("Кинув", "Dropped", "Брошено"),
    ;

    /** Подпись на текущем языке (только для показа; сохраняется [name]). */
    val label: String get() = tr(uk, en, ru)
}

@Serializable
data class LibraryEntry(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String? = null,
    val year: String? = null,
    val section: String,
    /** Ваша оценка 1..10 (целая, округлённая) — для совместимости со старыми версиями. */
    val rating: Int? = null,
    /** Точная оценка с десятыми (например 5.2). Если null — берём [rating]. */
    val ratingExact: Double? = null,
    val status: WatchStatus? = null,
    val note: String = "",
    /**
     * Свой постер: "tmdb:/путь.jpg" (другой постер с TMDB) или
     * "file:имя.jpg" (картинка из галереи, лежит в files/posters). null = оригинальный.
     */
    val customPoster: String? = null,
    /** Метка «Улюблене» ❤ — независимо от статуса. */
    val favorite: Boolean = false,
    /** Своё название (вместо названия TMDB). */
    val customTitle: String? = null,
    /** Свой фон карточки: "tmdb:/путь.jpg" или "file:имя.jpg". */
    val customBackdrop: String? = null,
    /** Жанры (названия) — для фильтров и статистики. */
    val genres: List<String> = emptyList(),
    /** Длительность: фильм — минуты, сериал — минут на серию. */
    val runtime: Int? = null,
    /** Трекер серий: номер сезона → просмотренные серии. */
    val episodes: Map<String, List<Int>> = emptyMap(),
    /** Свои подборки, в которые входит фильм. */
    val collections: List<String> = emptyList(),
    /** Фильм добавлен вручную (нет в TMDB). */
    val manual: Boolean = false,
    /** Описание для фильмов, добавленных вручную. */
    val overview: String? = null,
    /** Главные актёры (до 12, по порядку в титрах) — для фильтра «по актёру» в «Мої оцінки». */
    val cast: List<CastRef> = emptyList(),
    /** Когда отмечен просмотренным (для статистики по месяцам). */
    val watchedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val type: MediaType get() = MediaType.fromPath(mediaType) ?: MediaType.MOVIE
    val sectionEnum: Section get() = Section.fromName(section) ?: Section.MOVIES
    val key: String get() = mediaKey(type, tmdbId)
    val displayTitle: String get() = customTitle?.takeIf { it.isNotBlank() } ?: title
    /** Оценка для показа: точная, если есть, иначе целая. */
    val score: Double? get() = ratingExact ?: rating?.toDouble()

    /** Поставить оценку (с десятыми) — целое поле заполняется округлённым значением. */
    fun withScore(value: Double?): LibraryEntry {
        val v = value?.let { (Math.round(it * 10) / 10.0).coerceIn(1.0, 10.0) }
        return copy(ratingExact = v, rating = v?.let { Math.round(it).toInt().coerceIn(1, 10) })
    }
    val watchedEpisodeCount: Int get() = episodes.values.sumOf { it.size }
    val isEmpty: Boolean
        get() = rating == null && status == null && note.isBlank() && customPoster == null && !favorite &&
            customTitle == null && customBackdrop == null && episodes.isEmpty() && collections.isEmpty() && !manual
}

/** «5» для целых, «5.2» для дробных. */
fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

/** Актёр в библиотеке: id TMDB (чтобы не путать тёзок) и имя. */
@Serializable
data class CastRef(val id: Int, val name: String)

/** Главные актёры из титров TMDB: первые 12, без пустых имён. */
fun List<com.kinoteka.app.data.tmdb.CastMember>.topCast(): List<CastRef> =
    filter { it.id > 0 && it.name.isNotBlank() }.distinctBy { it.id }.take(12).map { CastRef(it.id, it.name) }

@Serializable
data class LibraryFile(
    val version: Int = 1,
    val entries: List<LibraryEntry> = emptyList(),
    val deleted: Map<String, Long> = emptyMap(),
    /** Свои подборки: название → когда создана (включая пустые). */
    val collections: Map<String, Long> = emptyMap(),
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

    /** Свои подборки: название → время создания. */
    private val _collections = MutableStateFlow<Map<String, Long>>(emptyMap())
    val collections: StateFlow<Map<String, Long>> = _collections.asStateFlow()

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
            _collections.update { loaded.collections + it }
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

    /**
     * Обновить название и жанры на другом языке (после смены языка приложения).
     * updatedAt не трогаем: это не ваше изменение, и синхронизация не должна из-за него гонять данные.
     */
    fun refreshMeta(key: String, title: String, genres: List<String>, cast: List<CastRef>? = null) {
        var changed = false
        _entries.update { map ->
            val e = map[key] ?: return@update map
            val newCast = cast?.takeIf { it.isNotEmpty() } ?: e.cast
            if (e.title == title && e.genres == genres && e.cast == newCast) return@update map
            changed = true
            map + (key to e.copy(title = title, genres = genres.ifEmpty { e.genres }, cast = newCast))
        }
        if (changed) persist()
    }

    /** Дописать актёров (подтянули с TMDB). updatedAt не трогаем — это не ваше изменение. */
    fun setCast(key: String, cast: List<CastRef>) {
        if (cast.isEmpty()) return
        var changed = false
        _entries.update { map ->
            val e = map[key] ?: return@update map
            if (e.cast == cast) return@update map
            changed = true
            map + (key to e.copy(cast = cast))
        }
        if (changed) persist()
    }

    fun remove(key: String) {
        _entries.update { it - key }
        deleted.update { it + (key to System.currentTimeMillis()) }
        localChanged()
    }

    /** Создать подборку. */
    fun createCollection(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n in _collections.value) return
        _collections.update { it + (n to System.currentTimeMillis()) }
        deleted.update { it - collectionKey(n) }
        localChanged()
    }

    /** Удалить подборку (фильмы остаются, просто убираются из неё). */
    fun deleteCollection(name: String) {
        val now = System.currentTimeMillis()
        _collections.update { it - name }
        deleted.update { it + (collectionKey(name) to now) }
        _entries.update { map ->
            map.mapValues { (_, e) ->
                if (name in e.collections) e.copy(collections = e.collections - name, updatedAt = now) else e
            }.filterValues { !it.isEmpty }
        }
        localChanged()
    }

    /** Добавить много записей сразу (импорт). Существующие не перезаписываются. */
    fun addAll(newEntries: List<LibraryEntry>) {
        _entries.update { map -> map + newEntries.filter { it.key !in map }.associateBy { it.key } }
        deleted.update { it - newEntries.map { e -> e.key }.toSet() }
        localChanged()
    }

    /** Всё содержимое для синхронизации. */
    fun snapshot(): LibraryFile = LibraryFile(
        entries = _entries.value.values.sortedByDescending { it.updatedAt },
        deleted = deleted.value,
        collections = _collections.value,
    )

    /** Заменить содержимое результатом синхронизации (без повторного запуска синхронизации). */
    fun replaceFromSync(file: LibraryFile) {
        // Сливаем ещё раз с текущим состоянием: вдруг вы что-то изменили, пока шла синхронизация.
        val final = merge(file, snapshot())
        _entries.value = final.entries.associateBy { it.key }
        deleted.value = final.deleted
        _collections.value = final.collections
        persist()
    }

    /**
     * Вход в другой аккаунт с его собственными оценками: очищаем телефон, следом синхронизация
     * загрузит оценки нового аккаунта. Прежние остаются на Диске прежнего аккаунта.
     */
    fun clearForAccountSwitch() {
        _entries.value = emptyMap()
        deleted.value = emptyMap()
        _collections.value = emptyMap()
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
            // Подборки: живы, если созданы позже, чем удалены.
            val allCollections = (a.collections.keys + b.collections.keys).associateWith { n ->
                maxOf(a.collections[n] ?: 0L, b.collections[n] ?: 0L)
            }
            val collections = allCollections.filter { (n, created) -> created > (deleted[collectionKey(n)] ?: 0L) }
            val liveKeys = aliveKeys + collections.keys.map(::collectionKey)
            val tombstones = deleted.filter { (k, t) -> k !in liveKeys && now - t < TOMBSTONE_TTL_MS }
            return LibraryFile(entries = alive.sortedByDescending { it.updatedAt }, deleted = tombstones, collections = collections)
        }

        private fun collectionKey(name: String) = "collection:$name"
    }
}
