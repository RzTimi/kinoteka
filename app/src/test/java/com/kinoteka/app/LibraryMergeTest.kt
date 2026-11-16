package com.kinoteka.app

import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.LibraryFile
import com.kinoteka.app.data.library.LibraryStore
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.data.library.formatScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Слияние телефона и Google Диска — самое ценное: ошибка здесь = потерянные оценки. */
class LibraryMergeTest {
    private val now = 1_800_000_000_000L

    private fun entry(id: Int, updatedAt: Long, rating: Int? = null, note: String = "") = LibraryEntry(
        mediaType = "movie", tmdbId = id, title = "Film $id", section = "MOVIES",
        rating = rating, status = WatchStatus.WATCHED, note = note, updatedAt = updatedAt,
    )

    @Test fun `newest version of an entry wins`() {
        val phone = LibraryFile(entries = listOf(entry(1, updatedAt = now - 10, rating = 5)))
        val drive = LibraryFile(entries = listOf(entry(1, updatedAt = now - 5, rating = 9)))
        val merged = LibraryStore.merge(phone, drive, now)
        assertEquals(1, merged.entries.size)
        assertEquals(9, merged.entries.single().rating)
        // Порядок аргументов не важен.
        assertEquals(9, LibraryStore.merge(drive, phone, now).entries.single().rating)
    }

    @Test fun `entries from both sides are kept`() {
        val phone = LibraryFile(entries = listOf(entry(1, now - 10)))
        val drive = LibraryFile(entries = listOf(entry(2, now - 10)))
        val keys = LibraryStore.merge(phone, drive, now).entries.map { it.tmdbId }.toSet()
        assertEquals(setOf(1, 2), keys)
    }

    @Test fun `deletion newer than the last edit removes the entry`() {
        val phone = LibraryFile(deleted = mapOf("movie:1" to now - 1))
        val drive = LibraryFile(entries = listOf(entry(1, updatedAt = now - 100)))
        val merged = LibraryStore.merge(phone, drive, now)
        assertTrue(merged.entries.isEmpty())
        assertTrue("movie:1" in merged.deleted)
    }

    @Test fun `edit newer than the deletion brings the entry back`() {
        val phone = LibraryFile(deleted = mapOf("movie:1" to now - 100))
        val drive = LibraryFile(entries = listOf(entry(1, updatedAt = now - 1)))
        val merged = LibraryStore.merge(phone, drive, now)
        assertEquals(1, merged.entries.size)
        assertFalse("movie:1" in merged.deleted)
    }

    @Test fun `old tombstones are dropped after half a year`() {
        val yearAgo = now - 365L * 24 * 60 * 60 * 1000
        val merged = LibraryStore.merge(LibraryFile(deleted = mapOf("movie:7" to yearAgo)), LibraryFile(), now)
        assertTrue(merged.deleted.isEmpty())
    }

    @Test fun `collections survive unless deleted later`() {
        val phone = LibraryFile(collections = mapOf("Жахи" to now - 50, "Старе" to now - 50))
        val drive = LibraryFile(deleted = mapOf("collection:Старе" to now - 10))
        val merged = LibraryStore.merge(phone, drive, now)
        assertEquals(setOf("Жахи"), merged.collections.keys)
    }

    @Test fun `score keeps tenths and rounds the old integer field`() {
        val e = entry(1, now).withScore(5.24)
        assertEquals(5.2, e.ratingExact!!, 0.0001)
        assertEquals(5, e.rating)
        assertEquals(6, entry(1, now).withScore(5.5).rating)
        assertEquals(10.0, entry(1, now).withScore(12.0).score!!, 0.0001)
        assertNull(entry(1, now, rating = 7).withScore(null).score)
        // Старые записи без ratingExact показывают целую оценку.
        assertEquals(7.0, entry(1, now, rating = 7).score!!, 0.0001)
    }

    @Test fun `score formatting`() {
        assertEquals("5", formatScore(5.0))
        assertEquals("5.2", formatScore(5.2))
        assertEquals("10", formatScore(10.0))
    }
}
