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
