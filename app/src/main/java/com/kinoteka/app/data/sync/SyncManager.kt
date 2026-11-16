package com.kinoteka.app.data.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.kinoteka.app.data.library.LibraryFile
import com.kinoteka.app.data.library.LibraryStore
import com.kinoteka.app.data.library.PosterStorage
import com.kinoteka.app.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class SyncState(
    val email: String? = null,
    val name: String? = null,
    val photo: String? = null,
    val syncing: Boolean = false,
    val lastSync: Long? = null,
    val error: String? = null,
    val folderId: String? = null,
    /** Токен перестал выдаваться без окна: нужно нажать «Увійти» ещё раз. */
    val needsReauth: Boolean = false,
    /** Вошли в другой аккаунт, а на телефоне оценки прежнего — ждём, что выберет пользователь. */
    val pendingSwitch: AccountSwitch? = null,
) {
    val signedIn: Boolean get() = email != null
}

/** Вход в другой аккаунт: чьи оценки сейчас на телефоне и есть ли несохранённые изменения. */
data class AccountSwitch(
    val email: String,
    val name: String?,
    val photo: String?,
    val previous: String,
    /** Изменения, сделанные после выхода из прежнего аккаунта (их нет на его Диске). */
    val unsynced: Boolean,
)

/**
 * Синхронизация с Google Диском:
 * папка «Кінотека» → data.json (оценки, статусы, заметки) + posters/ (свои постеры).
 * Запускается при старте, через несколько секунд после изменений и вручную.
 */
@OptIn(FlowPreview::class)
class SyncManager(
    context: Context,
    private val library: LibraryStore,
    private val posters: PosterStorage,
    private val auth: GoogleAuth,
    private val drive: DriveApi,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(
        SyncState(
            email = prefs.getString(KEY_EMAIL, null),
            name = prefs.getString(KEY_NAME, null),
            photo = prefs.getString(KEY_PHOTO, null),
            lastSync = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
            folderId = prefs.getString(KEY_FOLDER, null),
        )
    )
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Показывали ли приветствие «Увійти через Google?» при первом запуске. */
    var welcomeShown: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME, value).apply()

    /** Токен нового аккаунта, пока пользователь решает, что делать с оценками на телефоне. */
    private var pendingToken: String? = null

    init {
        // Старые версии не помнили, чей аккаунт хозяин данных на телефоне: считаем, что тот, в который вошли.
        if (!prefs.contains(KEY_OWNER)) _state.value.email?.let { prefs.edit().putString(KEY_OWNER, it).apply() }
        // Изменения без входа в аккаунт — помечаем: на Диске их пока нет.
        library.localVersion.drop(1).onEach {
            if (!_state.value.signedIn) prefs.edit().putBoolean(KEY_DIRTY, true).apply()
        }.launchIn(scope)
        // Синхронизация при запуске.
        scope.launch {
            library.loaded.first { it }
            if (_state.value.signedIn) sync()
        }
        // И через 4 секунды после последнего изменения на телефоне.
        library.localVersion.drop(1).debounce(4_000).onEach {
            if (_state.value.signedIn && !_state.value.needsReauth) sync()
        }.launchIn(scope)
    }

    /** Первый шаг входа: результат может потребовать окно выбора аккаунта. */
    suspend fun beginSignIn(): AuthorizationResult = auth.authorize()

    fun resultFromIntent(intent: android.content.Intent?): AuthorizationResult = auth.resultFromIntent(intent)

    /** Вход завершён: узнаём, кто вошёл, и сразу синхронизируемся. */
    fun onAuthorized(result: AuthorizationResult) {
        scope.launch {
            val token = result.accessToken
            if (token == null) {
                _state.update { it.copy(error = tr("Google не видав доступ до Диска", "Google didn't grant access to Drive", "Google не дал доступ к Диску")) }
                return@launch
            }
            runCatching { drive.about(token) }
                .onSuccess { user ->
                    val email = user.emailAddress ?: result.toGoogleSignInAccount()?.email
                    val owner = prefs.getString(KEY_OWNER, null)
                    if (email != null && needsSwitchPrompt(owner, email, library.entries.value.isNotEmpty())) {
                        // На телефоне оценки другого аккаунта — не смешиваем без спроса.
                        pendingToken = token
                        _state.update {
                            it.copy(
                                pendingSwitch = AccountSwitch(email, user.displayName, user.photoLink, owner!!, prefs.getBoolean(KEY_DIRTY, false)),
                                error = null,
                            )
                        }
                        return@onSuccess
                    }
                    commitAccount(email, user.displayName, user.photoLink)
                    sync(token)
                }
                .onFailure { e -> _state.update { it.copy(error = describe(e)) } }
        }
    }

    private fun commitAccount(email: String?, name: String?, photo: String?) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
            .putString(KEY_PHOTO, photo)
            .remove(KEY_FOLDER)
            .remove(KEY_LAST_SYNC)
            .apply()
        _state.update {
            it.copy(email = email, name = name, photo = photo, error = null, needsReauth = false, pendingSwitch = null, folderId = null, lastSync = null)
        }
    }

    /**
     * Выбор при входе в другой аккаунт.
     * [keepPhoneData] = false — загрузить оценки нового аккаунта (прежние остаются на Диске прежнего);
     * true — перенести то, что на телефоне, в новый аккаунт.
     */
    fun resolveSwitch(keepPhoneData: Boolean) {
        val sw = _state.value.pendingSwitch ?: return
        val token = pendingToken ?: return
        pendingToken = null
        scope.launch {
            if (!keepPhoneData) library.clearForAccountSwitch()
            commitAccount(sw.email, sw.name, sw.photo)
            sync(token)
        }
    }

    /** Передумали входить: остаёмся как были. */
    fun cancelSwitch() {
        pendingToken = null
        _state.update { it.copy(pendingSwitch = null) }
    }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    /** Запустить синхронизацию сейчас (кнопка «Синхронізувати»). */
    fun syncNow() {
        scope.launch { sync() }
    }

    /** Выйти: данные остаются и на телефоне, и на Диске. */
    fun signOut() {
        val email = _state.value.email ?: return
        scope.launch {
            mutex.withLock {
                auth.revoke(email)
                prefs.edit().remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_PHOTO).remove(KEY_LAST_SYNC).remove(KEY_FOLDER).apply()
                _state.value = SyncState()
            }
        }
    }

    private suspend fun sync(knownToken: String? = null) {
        val email = _state.value.email ?: return
        if (!mutex.tryLock()) return // уже идёт
        try {
            _state.update { it.copy(syncing = true, error = null) }
            var token = knownToken ?: auth.tokenSilently(email)
            if (token == null) {
                _state.update { it.copy(syncing = false, needsReauth = true, error = tr("Потрібно увійти знову", "Please sign in again", "Нужно войти снова")) }
                return
            }
            try {
                doSync(token)
            } catch (e: DriveException) {
                if (e.code != 401) throw e
                // Токен устарел: сбрасываем и пробуем один раз с новым.
                auth.clearToken(token)
                token = auth.tokenSilently(email) ?: throw e
                doSync(token)
            }
            val now = System.currentTimeMillis()
            // Теперь на телефоне — оценки этого аккаунта, и всё сохранено на его Диске.
            prefs.edit().putLong(KEY_LAST_SYNC, now).putString(KEY_OWNER, email).putBoolean(KEY_DIRTY, false).apply()
            _state.update { it.copy(syncing = false, lastSync = now, error = null, needsReauth = false) }
        } catch (e: Exception) {
            _state.update { it.copy(syncing = false, error = describe(e)) }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doSync(token: String) {
        // 1. Папки «Кінотека» и «Кінотека/posters».
        val folderId = ensureFolder(token, FOLDER_NAME, null)
        if (_state.value.folderId != folderId) {
            prefs.edit().putString(KEY_FOLDER, folderId).apply()
            _state.update { it.copy(folderId = folderId) }
        }
        val postersFolderId = ensureFolder(token, POSTERS_FOLDER, folderId)

        // 2. data.json: скачать, слить с телефоном, сохранить в оба места.
        val remoteFile = drive.find(token, DATA_FILE, folderId, folder = false)
        val remote = remoteFile?.let {
            runCatching { LibraryStore.JSON.decodeFromString<LibraryFile>(drive.download(token, it.id).decodeToString()) }.getOrNull()
        } ?: LibraryFile()
        val merged = LibraryStore.merge(library.snapshot(), remote)
        library.replaceFromSync(merged)
        val bytes = LibraryStore.JSON.encodeToString(LibraryFile.serializer(), merged).encodeToByteArray()
        if (remoteFile == null) drive.createFile(token, DATA_FILE, folderId, "application/json", bytes)
        else drive.updateFile(token, remoteFile.id, "application/json", bytes)

        // 3. Постеры: загрузить новые, скачать недостающие, убрать ненужные.
        val needed = merged.entries.mapNotNull { e ->
            e.customPoster?.takeIf { it.startsWith(PosterStorage.FILE_PREFIX) }?.removePrefix(PosterStorage.FILE_PREFIX)
        }.toSet()
        val remotePosters = drive.children(token, postersFolderId).associateBy { it.name }
        for (name in needed) {
            val local = posters.localFile(name)
            val onDrive = remotePosters[name]
            when {
                local.exists() && onDrive == null -> drive.createFile(token, name, postersFolderId, "image/jpeg", local.readBytes())
                !local.exists() && onDrive != null -> posters.writeDownloaded(name, drive.download(token, onDrive.id))
            }
        }
        for ((name, file) in remotePosters) {
            if (name !in needed) runCatching { drive.delete(token, file.id) }
        }
    }

    private suspend fun ensureFolder(token: String, name: String, parentId: String?): String =
        drive.find(token, name, parentId, folder = true)?.id ?: drive.createFolder(token, name, parentId)

    private fun describe(e: Throwable): String = when (e) {
        is DriveException -> when (e.code) {
            401, 403 -> tr("Немає доступу до Google Диска. Увійдіть знову", "No access to Google Drive. Please sign in again", "Нет доступа к Google Диску. Войдите снова")
            else -> tr("Помилка Google Диска (${e.code})", "Google Drive error (${e.code})", "Ошибка Google Диска (${e.code})")
        }
        is java.net.UnknownHostException -> tr("Немає з'єднання з інтернетом", "No internet connection", "Нет подключения к интернету")
        is IOException -> tr("Проблема з мережею", "Network problem", "Проблема с сетью")
        else -> GoogleAuth.describe(e)
    }

    companion object {
        const val FOLDER_NAME = "Кінотека"
        private const val POSTERS_FOLDER = "posters"
        private const val DATA_FILE = "data.json"

        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_PHOTO = "photo"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_FOLDER = "folder_id"
        private const val KEY_WELCOME = "welcome_shown"
        /** Чей аккаунт — хозяин оценок на телефоне (последняя успешная синхронизация). */
        private const val KEY_OWNER = "library_owner"
        /** Есть изменения, сделанные без входа (их нет ни на одном Диске). */
        private const val KEY_DIRTY = "dirty_since_sync"

        /**
         * Спросить при входе? Да — если на телефоне есть оценки, и они принадлежат другому аккаунту.
         * Оценки, которые ещё ни с кем не синхронизировались (owner = null), просто переносим в аккаунт.
         */
        fun needsSwitchPrompt(owner: String?, email: String, hasEntries: Boolean): Boolean =
            hasEntries && owner != null && !owner.equals(email, ignoreCase = true)
    }
}
