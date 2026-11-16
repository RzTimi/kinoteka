package com.kinoteka.app

import com.kinoteka.app.data.share.ShareLink
import com.kinoteka.app.data.share.SharedItem
import com.kinoteka.app.data.share.SharedPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ссылки «Открыть в Кінотеці»: что закодировали — то и прочитали, и страница их понимает. */
class ShareLinkTest {
    private val list = SharedPayload(
        title = "Жахи на вечір",
        items = listOf(
            SharedItem("m", 238, "Хрещений батько", "1972", "/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", "MOVIES", 9.5),
            SharedItem("t", 1396, "Пуститися берега", "2008", null, "SERIES", null),
        ),
    )

    @Test fun `round trip keeps everything including cyrillic`() {
        val decoded = ShareLink.decode(ShareLink.encode(list))
        assertEquals(list, decoded)
    }

    @Test fun `encoded data is url safe`() {
        val encoded = ShareLink.encode(list)
        assertTrue(encoded.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(ShareLink.url(list).startsWith("https://rztimi.github.io/kinoteka/open/#d="))
    }

    @Test fun `single movie is recognised`() {
        val one = SharedPayload(items = listOf(list.items.first()))
        assertTrue(ShareLink.decode(ShareLink.encode(one))!!.isSingle)
        assertFalse(list.isSingle)
    }

    @Test fun `garbage and empty lists are rejected`() {
        assertNull(ShareLink.decode("not-a-link"))
        assertNull(ShareLink.decode(""))
        assertNull(ShareLink.decode(ShareLink.encode(SharedPayload(title = "x", items = emptyList()))))
    }

    /**
     * Ссылка, сжатая не приложением, а сторонним deflate (так же, как это сделала бы страница или
     * другой клиент): формат raw deflate + base64url должен читаться одинаково.
     */
    @Test fun `reads a link encoded outside the app`() {
        val external = "q1YqU7Iy1FHKVLKKrlbKVrJSylUC8lKUrMwsDHSU8oACF2ZdbLiw48IeIN57YcOF3Rd7gPS-C1sVLrZfbAIKbrqwD6ilEqjS0NLSBMgsADL1U0wzPXPc8kyLDTxzi6siq5wCkiy8AjzTkiJc9LIK0oHKioHKfP3DPF2DgZwiJSsLPYPa2FoA"
        val p = ShareLink.decode(external)!!
        assertTrue(p.isSingle)
        assertEquals("Криминальное чтиво", p.items.single().title)
        assertEquals(680, p.items.single().id)
        assertEquals(8.0, p.items.single().score!!, 0.0001)
    }
}

class ShareTextTest {
    private fun e(id: Int, title: String, score: Double? = null) =
        com.kinoteka.app.data.library.LibraryEntry(mediaType = "movie", tmdbId = id, title = title, year = "2026", section = "MOVIES")
            .withScore(score)

    @Test fun `list text has one kinoteka link at the end and no tmdb links`() {
        com.kinoteka.app.i18n.I18n.language = com.kinoteka.app.i18n.AppLanguage.RU
        val text = com.kinoteka.app.ui.share.listShareText("тест", listOf(e(1, "Дюна"), e(2, "Сердце зверя", 5.9)))
        assertFalse(text.contains("themoviedb.org"))
        assertEquals(1, Regex(Regex.escape("rztimi.github.io/kinoteka/open/#d=")).findAll(text).count())
        assertTrue(text.trimEnd().lines().last().startsWith("https://rztimi.github.io/kinoteka/open/#d="))
        assertTrue(text.contains("2. Сердце зверя (2026) — 🐍 5.9"))
        com.kinoteka.app.i18n.I18n.language = com.kinoteka.app.i18n.AppLanguage.UK
    }

    @Test fun `long caption is trimmed but keeps the link`() {
        val many = (1..60).map { e(it, "Дуже довга назва фільму номер $it") }
        val caption = com.kinoteka.app.ui.share.listShareText("Велика", many.take(10), maxChars = com.kinoteka.app.ui.share.CAPTION_LIMIT)
        assertTrue(caption.length <= com.kinoteka.app.ui.share.CAPTION_LIMIT)
        assertTrue(caption.contains("rztimi.github.io/kinoteka/open/#d="))
        val huge = com.kinoteka.app.ui.share.listShareText("Велика", many, maxChars = com.kinoteka.app.ui.share.CAPTION_LIMIT)
        assertTrue(huge.length <= com.kinoteka.app.ui.share.CAPTION_LIMIT)
    }

    @Test fun `movie caption for an image has no tmdb link`() {
        val caption = com.kinoteka.app.ui.share.movieShareText("Дюна", "2026", com.kinoteka.app.data.MediaType.MOVIE, 1, 5.2, null, withTmdb = false)
        assertFalse(caption.contains("themoviedb.org"))
        assertTrue(caption.contains("rztimi.github.io/kinoteka/open/#d="))
        val text = com.kinoteka.app.ui.share.movieShareText("Дюна", "2026", com.kinoteka.app.data.MediaType.MOVIE, 1, 5.2, null)
        assertTrue(text.contains("themoviedb.org/movie/1"))
    }
}
