package com.kinoteka.app.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data object HomeRoute
@Serializable data object GenresRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object AboutRoute

/** Список фильмов раздела: «Усі» из ленты главной или жанр. */
@Serializable
data class BrowseRoute(
    val section: String,
    val type: String,
    val title: String,
    /** Доп. параметры discover, закодированные в JSON. */
    val paramsJson: String = "{}",
    val sortable: Boolean = false,
)

@Serializable
data class DetailsRoute(val type: String, val id: Int)

object RouteParams {
    private val json = Json
    fun encode(params: Map<String, String>): String = json.encodeToString(params)
    fun decode(s: String): Map<String, String> = runCatching { json.decodeFromString<Map<String, String>>(s) }.getOrDefault(emptyMap())
}
