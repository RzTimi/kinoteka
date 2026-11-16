package com.kinoteka.app

import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.ui.search.SearchFilters
import com.kinoteka.app.ui.search.SearchRuntime
import com.kinoteka.app.ui.search.SearchSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchFiltersTest {
    private fun card(year: String?, rating: Double, genres: List<Int>, type: MediaType = MediaType.MOVIE, id: Int = 1) =
        MediaCard(id, type, "X", null, year, rating, if (type == MediaType.TV) Section.SERIES else Section.MOVIES, genreIds = genres)

    @Test fun `year and rating filters`() {
        val f = SearchFilters(yearFrom = 1990, yearTo = 1999, minRating = 7)
        assertTrue(f.matches(card("1994", 8.5, emptyList()), null, emptyMap()))
        assertFalse(f.matches(card("2001", 8.5, emptyList()), null, emptyMap()))
        assertFalse(f.matches(card("1994", 6.9, emptyList()), null, emptyMap()))
        assertFalse(f.matches(card(null, 9.0, emptyList()), null, emptyMap()))
    }

    @Test fun `movie genres are translated for tv shows`() {
        // «Бойовик» (28) у сериалов — «Бойовик і пригоди» (10759).
        val f = SearchFilters(genres = setOf(28))
        assertTrue(f.matches(card("2020", 8.0, listOf(10759), MediaType.TV), null, emptyMap()))
        assertFalse(f.matches(card("2020", 8.0, listOf(18), MediaType.TV), null, emptyMap()))
        // «Жахи» (27) у сериалов нет — сериал не подходит.
        assertFalse(SearchFilters(genres = setOf(27)).matches(card("2020", 8.0, listOf(27), MediaType.TV), null, emptyMap()))
    }

    @Test fun `runtime filter needs a known runtime`() {
        val f = SearchFilters(runtime = SearchRuntime.SHORT)
        assertTrue(f.matches(card("2020", 7.0, emptyList()), 85, emptyMap()))
        assertFalse(f.matches(card("2020", 7.0, emptyList()), 130, emptyMap()))
        assertFalse(f.matches(card("2020", 7.0, emptyList()), null, emptyMap()))
    }

    @Test fun `hide seen uses the library`() {
        val seen = LibraryEntry(mediaType = "movie", tmdbId = 1, title = "X", section = "MOVIES", rating = 6)
        val f = SearchFilters(hideSeen = true)
        assertFalse(f.matches(card("2020", 7.0, emptyList()), null, mapOf(seen.key to seen)))
        assertTrue(f.matches(card("2020", 7.0, emptyList(), id = 2), null, mapOf(seen.key to seen)))
    }

    @Test fun `discover params for movies`() {
        val p = SearchFilters(yearFrom = 2010, yearTo = 2019, runtime = SearchRuntime.MEDIUM, minRating = 7, genres = setOf(35), sort = SearchSort.RATING)
            .discoverParams(MediaType.MOVIE)!!
        assertEquals("2010-01-01", p["primary_release_date.gte"])
        assertEquals("2019-12-31", p["primary_release_date.lte"])
        assertEquals("90", p["with_runtime.gte"])
        assertEquals("120", p["with_runtime.lte"])
        assertEquals("7", p["vote_average.gte"])
        assertEquals("35", p["with_genres"])
        assertEquals("vote_average.desc", p["sort_by"])
        assertNull(p["without_genres"])
    }

    @Test fun `discover params for tv skip talk shows and impossible genres`() {
        val p = SearchFilters(genres = setOf(12, 28)).discoverParams(MediaType.TV)!!
        assertEquals("10759", p["with_genres"])
        assertEquals("10763,10764,10767", p["without_genres"])
        assertNull(SearchFilters(genres = setOf(27)).discoverParams(MediaType.TV))
    }

    @Test fun `newest first does not include future releases`() {
        val today = LocalDate.of(2026, 11, 16)
        val p = SearchFilters(sort = SearchSort.NEWEST).discoverParams(MediaType.MOVIE, today)!!
        assertEquals("2026-11-16", p["primary_release_date.lte"])
    }
}
