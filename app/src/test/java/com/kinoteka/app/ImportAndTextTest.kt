package com.kinoteka.app

import com.kinoteka.app.data.update.UpdateChecker
import com.kinoteka.app.i18n.AppLanguage
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.plural
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.library.parseImportCsv
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCsvTest {
    @Test fun `letterboxd five star scale is doubled and keeps halves`() {
        val csv = "Date,Name,Year,Letterboxd URI,Rating\n" +
            "2024-01-05,Dune,2021,https://boxd.it/x,4.5\n" +
            "2024-02-01,\"Crouching Tiger, Hidden Dragon\",2000,https://boxd.it/y,3\n"
        val rows = parseImportCsv(csv)
        assertEquals(2, rows.size)
        assertEquals("Dune", rows[0].title)
        assertEquals(2021, rows[0].year)
        assertEquals(9.0, rows[0].rating!!, 0.0001)
        // Запятая внутри кавычек — часть названия.
        assertEquals("Crouching Tiger, Hidden Dragon", rows[1].title)
        assertEquals(6.0, rows[1].rating!!, 0.0001)
        assertNotNull(rows[0].watchedAt)
    }

    @Test fun `ten point scale with semicolons and russian headers`() {
        val csv = "Название;Год;Оценка\nАмели;2001;8,5\nБрат;1997;10\n"
        val rows = parseImportCsv(csv)
        assertEquals(2, rows.size)
        assertEquals("Амели", rows[0].title)
        assertEquals(8.5, rows[0].rating!!, 0.0001)
        assertEquals(10.0, rows[1].rating!!, 0.0001)
    }

    @Test fun `empty rating stays empty`() {
        val rows = parseImportCsv("Name,Year,Rating\nAlien,1979,\n")
        assertNull(rows.single().rating)
    }
}

class PluralAndLanguageTest {
    @After fun reset() { I18n.language = AppLanguage.UK }

    private fun ep(n: Int) = plural(n, "серія|серії|серій", "episode|episodes", "серия|серии|серий")

    @Test fun `ukrainian forms`() {
        I18n.language = AppLanguage.UK
        assertEquals(listOf("серія", "серії", "серій", "серій", "серія", "серії", "серій", "серій"),
            listOf(1, 2, 5, 11, 21, 22, 25, 111).map(::ep))
    }

    @Test fun `russian forms`() {
        I18n.language = AppLanguage.RU
        assertEquals(listOf("серия", "серии", "серий", "серий", "серия", "серий"), listOf(1, 3, 7, 12, 101, 0).map(::ep))
    }

    @Test fun `english forms`() {
        I18n.language = AppLanguage.EN
        assertEquals(listOf("episode", "episodes", "episodes", "episodes"), listOf(1, 2, 21, 0).map(::ep))
    }

    @Test fun `tr follows the language`() {
        I18n.language = AppLanguage.EN
        assertEquals("Movies", tr("Фільми", "Movies", "Фильмы"))
        I18n.language = AppLanguage.RU
        assertEquals("Фильмы", tr("Фільми", "Movies", "Фильмы"))
    }
}

class UpdateVersionTest {
    @Test fun `version comparison is numeric`() {
        assertTrue(UpdateChecker.isNewer("0.4.0", "0.3.2"))
        assertTrue(UpdateChecker.isNewer("v0.10.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.3.2", "0.3.2"))
        assertFalse(UpdateChecker.isNewer("0.3.1", "0.3.2"))
        assertFalse(UpdateChecker.isNewer("0.3", "0.3.0"))
    }

    @Test fun `release notes markdown becomes plain text`() {
        val md = "**Що нового**\r\n- Посилання\n- `drive.file`\n\n\n\n## Кінець"
        assertEquals("Що нового\n• Посилання\n• drive.file\n\nКінець", UpdateChecker.cleanNotes(md))
    }
}
