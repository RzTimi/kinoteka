package com.kinoteka.app.data.tmdb

import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Interceptor
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

interface TmdbApi {
    @GET("discover/{type}")
    suspend fun discover(
        @Path("type") type: String,
        @QueryMap params: Map<String, String>,
    ): PagedResponse<TmdbItem>

    @GET("genre/{type}/list")
    suspend fun genres(@Path("type") type: String): GenreListResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String? = null,
    ): PagedResponse<TmdbItem>

    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,videos,watch/providers,recommendations,similar,external_ids",
        @Query("include_video_language") videoLanguages: String = "uk,en,null",
        @Query("language") language: String? = null,
    ): TmdbDetails

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("primary_release_year") year: Int?,
        @Query("include_adult") includeAdult: Boolean = false,
    ): PagedResponse<TmdbItem>

    /** Сезон сериала со списком серий. */
    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("language") language: String? = null,
    ): SeasonDetails

    /** Актёр/режиссёр: биография и все фильмы и сериалы. */
    @GET("person/{id}")
    suspend fun person(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "combined_credits,external_ids",
        @Query("language") language: String? = null,
    ): PersonDetails

    /** Все постеры фильма/сериала: украинские, английские и без текста. */
    @GET("{type}/{id}/images")
    suspend fun images(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("include_image_language") languages: String = "uk,en,null",
    ): ImagesResponse

    companion object {
        const val LANGUAGE = "uk-UA"
        const val REGION = "UA"

        fun create(apiKey: String, client: OkHttpClient, language: () -> String = { LANGUAGE }): TmdbApi {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }
            val authClient = client.newBuilder()
                .addInterceptor(authInterceptor(apiKey, language))
                .addInterceptor(offlineFallback())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(authClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
        }

        /**
         * Нет интернета — отдаём то, что уже загружали (до 7 дней назад), из дискового кэша.
         * Если в кэше ничего нет — пробрасываем исходную ошибку сети.
         */
        private fun offlineFallback() = Interceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                val cached = chain.proceed(
                    chain.request().newBuilder()
                        .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(7, TimeUnit.DAYS).build())
                        .build()
                )
                if (cached.isSuccessful) cached else {
                    cached.close()
                    throw e
                }
            }
        }

        /**
         * Принимает оба вида ключа TMDB: короткий "API Key" (v3) и длинный
         * "Read Access Token" (начинается с eyJ). Плюс подставляет украинский язык.
         */
        private fun authInterceptor(apiKey: String, language: () -> String) = Interceptor { chain ->
            val original = chain.request()
            val url = original.url.newBuilder().apply {
                if (original.url.queryParameter("language") == null) addQueryParameter("language", language())
                if (!apiKey.startsWith("eyJ")) addQueryParameter("api_key", apiKey)
            }.build()
            val request = original.newBuilder().url(url).apply {
                if (apiKey.startsWith("eyJ")) header("Authorization", "Bearer $apiKey")
                header("Accept", "application/json")
            }.build()
            chain.proceed(request)
        }
    }
}

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?, size: String = "w342") = path?.let { BASE + size + it }
    fun backdrop(path: String?, size: String = "w1280") = path?.let { BASE + size + it }
    fun profile(path: String?, size: String = "w185") = path?.let { BASE + size + it }
    fun logo(path: String?) = path?.let { BASE + "w92" + it }
    fun still(path: String?) = path?.let { BASE + "w300" + it }
}
