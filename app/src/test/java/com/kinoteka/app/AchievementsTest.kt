package com.kinoteka.app

import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.ui.stats.SnakeHat
import com.kinoteka.app.ui.stats.SnakeProgress
import com.kinoteka.app.ui.stats.SnakeSkin
import com.kinoteka.app.ui.stats.Unlock
import com.kinoteka.app.ui.stats.achievements
import com.kinoteka.app.ui.stats.longestMonthStreak
import com.kinoteka.app.ui.stats.snakeLevelIndex
import com.kinoteka.app.ui.stats.snakeLevels
import com.kinoteka.app.ui.stats.snakePointsDetailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class AchievementsTest {
    private fun movie(id: Int, year: String = "2020", score: Double? = null, genres: List<String> = emptyList(), runtime: Int? = null) =
        LibraryEntry(mediaType = "movie", tmdbId = id, title = "M$id", year = year, section = "MOVIES",
            status = WatchStatus.WATCHED, genres = genres, runtime = runtime).withScore(score)

    @Test fun `there are many achievements and ids are unique`() {
        val list = achievements(emptyList(), 0)
        assertTrue("achievements: ${list.size}", list.size >= 60)
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.none { it.unlocked })
    }

    @Test fun `every wardrobe item points to a real achievement or level`() {
        val ids = achievements(emptyList(), 0).map { it.id }.toSet()
        val unlocks = SnakeSkin.entries.map { it.unlock } + SnakeHat.entries.map { it.unlock }
        unlocks.forEach { u ->
            when (u) {
                is Unlock.AchievementId -> assertTrue("missing ${u.id}", u.id in ids)
                is Unlock.Level -> assertTrue(u.index in snakeLevels.indices)
                else -> Unit
            }
        }
    }

    @Test fun `tiers unlock step by step`() {
        val ten = (1..10).map { movie(it) }
        val list = achievements(ten, 0)
        assertTrue(list.first { it.id == "movies0" }.unlocked)   // 10 фильмов
        assertFalse(list.first { it.id == "movies1" }.unlocked)  // 50 фильмов
        assertTrue(list.first { it.id == "first_watch0" || it.id == "first_watch" }.unlocked)
    }

    @Test fun `genres match in all three languages`() {
        val horror = listOf(movie(1, genres = listOf("Жахи")), movie(2, genres = listOf("Ужасы")), movie(3, genres = listOf("Horror")),
            movie(4, genres = listOf("Horror")), movie(5, genres = listOf("Жахи")))
        assertTrue(achievements(horror, 0).first { it.id == "horror0" }.unlocked)
    }

    @Test fun `full spectrum needs every rating 1 to 10`() {
        val nine = (1..9).map { movie(it, score = it.toDouble()) }
        assertFalse(achievements(nine, 0).first { it.id == "spectrum" }.unlocked)
        assertTrue(achievements(nine + movie(10, score = 10.0), 0).first { it.id == "spectrum" }.unlocked)
    }

    @Test fun `month streak`() {
        val m = YearMonth.of(2026, 1)
        assertEquals(3, longestMonthStreak(setOf(m, m.plusMonths(1), m.plusMonths(2), m.plusMonths(5))))
        assertEquals(0, longestMonthStreak(emptySet()))
        // Через границу года.
        assertEquals(2, longestMonthStreak(setOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1))))
    }

    @Test fun `achievements add snake points and levels grow`() {
        val p = snakePointsDetailed((1..10).map { movie(it, score = 7.0) }, collections = 1)
        assertEquals(10, p.titles)
        assertTrue(p.achievements > 0)
        assertEquals(0, snakeLevelIndex(0))
        assertEquals(snakeLevels.lastIndex, snakeLevelIndex(100_000))
        assertTrue(snakeLevels.zipWithNext().all { (a, b) -> a.from < b.from })
    }

    @Test fun `wardrobe unlock rules`() {
        val none = SnakeProgress(levelIndex = 0, unlockedIds = emptySet())
        assertTrue(none.isOpen(SnakeSkin.GREEN.unlock))
        assertFalse(none.isOpen(SnakeHat.CROWN.unlock))
        val king = SnakeProgress(levelIndex = 10, unlockedIds = setOf("movies0"))
        assertTrue(king.isOpen(SnakeHat.CROWN.unlock))
        assertTrue(king.isOpen(SnakeHat.CAP.unlock))
    }
}
