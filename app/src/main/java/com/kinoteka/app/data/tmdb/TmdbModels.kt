package com.kinoteka.app.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

/** Элемент списка: фильм или сериал (у TMDB поля немного отличаются). */
@Serializable
data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("media_type") val mediaType: String? = null,
    val overview: String? = null,
    val popularity: Double = 0.0,
    /** Для фильмографии актёра: роль или должность. */
    val character: String? = null,
    val job: String? = null,
)

@Serializable
data class Genre(val id: Int, val name: String)

@Serializable
data class GenreListResponse(val genres: List<Genre> = emptyList())

@Serializable
data class TmdbDetails(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<Genre> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val status: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val credits: Credits? = null,
    val videos: Videos? = null,
    @SerialName("watch/providers") val watchProviders: WatchProvidersResponse? = null,
    val recommendations: PagedResponse<TmdbItem>? = null,
    val similar: PagedResponse<TmdbItem>? = null,
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
    @SerialName("next_episode_to_air") val nextEpisode: EpisodeBrief? = null,
    @SerialName("last_episode_to_air") val lastEpisode: EpisodeBrief? = null,
)

@Serializable
data class EpisodeBrief(
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 0,
)

@Serializable
data class SeasonDetails(
    val name: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
data class Episode(
    @SerialName("episode_number") val number: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class ExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
data class PersonDetails(
    val id: Int,
    val name: String = "",
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("known_for_department") val knownFor: String? = null,
    @SerialName("combined_credits") val credits: PersonCredits? = null,
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
)

@Serializable
data class PersonCredits(
    val cast: List<TmdbItem> = emptyList(),
    val crew: List<TmdbItem> = emptyList(),
)

@Serializable
data class Season(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class Credits(val cast: List<CastMember> = emptyList())

@Serializable
data class CastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class Videos(val results: List<Video> = emptyList())

@Serializable
data class Video(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val name: String = "",
    @SerialName("iso_639_1") val language: String? = null,
)

@Serializable
data class ImagesResponse(
    val posters: List<ImageInfo> = emptyList(),
    val backdrops: List<ImageInfo> = emptyList(),
)

@Serializable
data class ImageInfo(
    @SerialName("file_path") val filePath: String = "",
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class WatchProvidersResponse(val results: Map<String, CountryProviders> = emptyMap())

@Serializable
data class CountryProviders(
    val link: String? = null,
    val flatrate: List<Provider> = emptyList(),
    val free: List<Provider> = emptyList(),
    val ads: List<Provider> = emptyList(),
    val rent: List<Provider> = emptyList(),
    val buy: List<Provider> = emptyList(),
)

@Serializable
data class Provider(
    @SerialName("provider_id") val id: Int = 0,
    @SerialName("provider_name") val name: String = "",
    @SerialName("logo_path") val logoPath: String? = null,
)

/** Отзыв зрителя с TMDB (почти все — на английском). */
@Serializable
data class Review(
    val id: String = "",
    val author: String = "",
    @SerialName("author_details") val authorDetails: ReviewAuthor? = null,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val url: String? = null,
) {
    val authorName: String get() = authorDetails?.name?.takeIf { it.isNotBlank() } ?: author.ifBlank { authorDetails?.username.orEmpty() }
    val rating: Double? get() = authorDetails?.rating?.takeIf { it > 0 }

    /** Аватар: у старых аккаунтов TMDB отдаёт «/https://gravatar…» — тогда это готовая ссылка. */
    val avatarUrl: String? get() = authorDetails?.avatarPath?.takeIf { it.isNotBlank() }?.let { p ->
        if (p.startsWith("/http")) p.drop(1) else TmdbImages.profile(p, "w45")
    }
}

@Serializable
data class ReviewAuthor(
    val name: String? = null,
    val username: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    val rating: Double? = null,
)

/**
 * Текст отзыва без разметки: TMDB хранит его с Markdown (**жирный**, _курсив_) и
 * иногда с HTML-тегами. Оставляем только абзацы.
 */
fun cleanReviewText(raw: String): String = raw
    .replace("\r\n", "\n").replace('\r', '\n')
    .replace(Regex("""(?i)<br\s*/?>"""), "\n")
    .replace(Regex("""<[^>]{1,40}>"""), "")
    .replace(Regex("""\[([^\]]+)]\((https?://[^)]+)\)"""), "$1")
    .replace(Regex("""(\*\*|__)(.+?)\1"""), "$2")
    .replace(Regex("""(?<![\w*])[*_]([^*_\n]+)[*_](?![\w*])"""), "$1")
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
    .lines().joinToString("\n") { it.trimEnd() }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
