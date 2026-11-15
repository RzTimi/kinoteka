package com.kinoteka.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.UiSettings
import com.kinoteka.app.data.library.LibraryStore
import com.kinoteka.app.data.library.PosterStorage
import com.kinoteka.app.data.reminders.Reminders
import com.kinoteka.app.data.sync.DriveApi
import com.kinoteka.app.data.sync.GoogleAuth
import com.kinoteka.app.data.sync.SyncManager
import com.kinoteka.app.data.tmdb.TmdbApi
import com.kinoteka.app.widget.KinotekaWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import com.kinoteka.app.ui.details.displayTitle
import kotlinx.coroutines.flow.first

class KinotekaApp : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Напоминания: восстанавливаем расписание при запуске.
        Reminders.schedule(this, container.ui.reminders.value)
        // Виджет обновляется через пару секунд после изменений в библиотеке.
        container.library.entries.drop(1).debounce(2_000).onEach {
            runCatching { KinotekaWidget().updateAll(this@KinotekaApp) }
        }.launchIn(appScope)
        // Названия и жанры в библиотеке — на языке приложения: после смены языка подтягиваем заново.
        container.ui.appLanguage.onEach { lang ->
            container.library.loaded.first { it }
            val prefs = getSharedPreferences("ui", MODE_PRIVATE)
            if (prefs.getString("library_meta_language", "uk") == lang.code) return@onEach
            for (e in container.library.entries.value.values.toList()) {
                if (e.manual || container.ui.appLanguage.value != lang) continue
                val d = runCatching { container.repository.detailsLite(e.type, e.tmdbId) }.getOrNull() ?: continue
                container.library.refreshMeta(
                    e.key,
                    d.displayTitle(e.type),
                    d.genres.map { g -> g.name.replaceFirstChar { c -> c.uppercase() } },
                )
            }
            if (container.ui.appLanguage.value == lang) prefs.edit().putString("library_meta_language", lang.code).apply()
        }.launchIn(appScope)
    }
}

/** Простейший «контейнер зависимостей»: всё, что нужно экранам. */
class AppContainer(app: Application) {
    val hasApiKey: Boolean = BuildConfig.TMDB_API_KEY.isNotBlank()
    val ui = UiSettings(app)
    // Дисковый HTTP-кэш: ответы TMDB живут по их заголовкам (~6 часов), без интернета — до 7 дней.
    private val http = OkHttpClient.Builder()
        .cache(Cache(File(app.cacheDir, "http"), 30L * 1024 * 1024))
        .build()
    val repository = MediaRepository(
        TmdbApi.create(BuildConfig.TMDB_API_KEY, http) { ui.contentLanguage.value },
        http,
        BuildConfig.OMDB_API_KEY,
    )
    val library = LibraryStore(app)
    val posters = PosterStorage(app)
    val sync = SyncManager(app, library, posters, GoogleAuth(app), DriveApi(http))
    /** Данные из ссылки «Открыть в Кінотеці», которые ещё не показали. */
    val pendingLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
