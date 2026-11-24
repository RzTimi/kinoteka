package com.kinoteka.app

import com.kinoteka.app.data.tmdb.Review
import com.kinoteka.app.data.tmdb.ReviewAuthor
import com.kinoteka.app.data.tmdb.cleanReviewText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewTextTest {
    @Test
    fun stripsMarkdownAndHtml() {
        val raw = "**Great** movie, _really_.<br/>Second line<em>!</em>\r\n\r\n\r\n\r\nSee [here](https://example.com) &amp; more"
        assertEquals("Great movie, really.\nSecond line!\n\nSee here & more", cleanReviewText(raw))
    }

    @Test
    fun stripsQuotesAndNestedEmphasis() {
        assertEquals("An uncategorisable masterpiece\n\nWe all create", cleanReviewText("**_An uncategorisable masterpiece_**\r\n\r\n>_We all create_"))
    }

    @Test
    fun keepsSnakeCaseAndMath() {
        assertEquals("file_name_here and 2*3*4", cleanReviewText("file_name_here and 2*3*4"))
    }

    @Test
    fun authorAvatarAndRating() {
        val gravatar = Review(author = "x", authorDetails = ReviewAuthor(avatarPath = "/https://secure.gravatar.com/a.jpg", rating = 0.0))
        assertEquals("https://secure.gravatar.com/a.jpg", gravatar.avatarUrl)
        assertNull(gravatar.rating)
        val tmdb = Review(author = "nick", authorDetails = ReviewAuthor(name = "Nick B", avatarPath = "/abc.jpg", rating = 8.0))
        assertEquals("https://image.tmdb.org/t/p/w45/abc.jpg", tmdb.avatarUrl)
        assertEquals("Nick B", tmdb.authorName)
        assertEquals(8.0, tmdb.rating!!, 0.0)
    }
}
