package com.kinoteka.app

import android.app.Application
import com.kinoteka.app.data.MediaRepository
import com.kinoteka.app.data.library.LibraryStore
import com.kinoteka.app.data.library.PosterStorage
import com.kinoteka.app.data.sync.DriveApi
import com.kinoteka.app.data.sync.GoogleAuth
import com.kinoteka.app.data.sync.SyncManager
import com.kinoteka.app.data.tmdb.TmdbApi
import okhttp3.OkHttpClient

class KinotekaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Простейший «контейнер зависимостей»: всё, что нужно экранам. */
class AppContainer(app: Application) {
    val hasApiKey: Boolean = BuildConfig.TMDB_API_KEY.isNotBlank()
    private val http = OkHttpClient()
    val repository = MediaRepository(TmdbApi.create(BuildConfig.TMDB_API_KEY, http))
    val library = LibraryStore(app)
    val posters = PosterStorage(app)
    val sync = SyncManager(app, library, posters, GoogleAuth(app), DriveApi(http))
}
