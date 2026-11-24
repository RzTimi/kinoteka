package com.kinoteka.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kinoteka.app.data.library.formatScore
import com.kinoteka.app.data.tmdb.Review
import com.kinoteka.app.data.tmdb.cleanReviewText
import com.kinoteka.app.i18n.AppLanguage
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.tr
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * «Відгуки глядачів»: карточки отзывов с TMDB в ряд. Нажатие открывает отзыв целиком,
 * оттуда можно перевести его (Google Переводчик) или открыть на TMDB.
 */
@Composable
fun ReviewsRow(reviews: List<Review>, tmdbPage: String) {
    var opened by remember { mutableStateOf<Review?>(null) }
    val uri = LocalUriHandler.current
    if (I18n.language != AppLanguage.EN) {
        Text(
            tr("Відгуки з TMDB — здебільшого англійською. Натисніть на відгук, щоб прочитати й перекласти.",
                "Reviews from TMDB. Tap a review to read it in full.",
                "Отзывы с TMDB — в основном на английском. Нажмите на отзыв, чтобы прочитать и перевести."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(reviews, key = { it.id }) { r -> ReviewCard(r) { opened = r } }
        item(key = "all") {
            Card(
                Modifier.width(150.dp).height(200.dp).clickable { uri.openUri(tmdbPage) },
            ) {
                Box(Modifier.fillMaxWidth().height(200.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        tr("Усі відгуки на TMDB ›", "All reviews on TMDB ›", "Все отзывы на TMDB ›"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    opened?.let { r -> ReviewDialog(r, onDismiss = { opened = null }) }
}

@Composable
private fun ReviewCard(r: Review, onClick: () -> Unit) {
    val text = remember(r.id) { cleanReviewText(r.content) }
    Card(Modifier.width(290.dp).height(200.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            ReviewHeader(r)
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReviewHeader(r: Review) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(r)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            reviewDate(r.createdAt)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        r.rating?.let { score ->
            Surface(shape = RoundedCornerShape(8.dp), color = ratingColor(score)) {
                Text(
                    "★ ${formatScore(score)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun Avatar(r: Review) {
    val url = r.avatarUrl
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            r.authorName.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (url != null) AsyncImage(url, null, Modifier.size(36.dp), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ReviewDialog(r: Review, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val text = remember(r.id) { cleanReviewText(r.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { ReviewHeader(r) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Закрити", "Close", "Закрыть")) } },
        dismissButton = {
            Row {
                if (I18n.language != AppLanguage.EN) {
                    TextButton(onClick = { uri.openUri(translateUrl(text)) }) { Text(tr("Перекласти", "Translate", "Перевести")) }
                }
                r.url?.let { link -> TextButton(onClick = { uri.openUri(link) }) { Text("TMDB") } }
            }
        },
    )
}

/** Google Переводчик с текстом отзыва на язык приложения (длинные отзывы обрезаем — лимит ссылки). */
private fun translateUrl(text: String): String =
    "https://translate.google.com/?sl=auto&tl=${I18n.language.code}&op=translate&text=" +
        URLEncoder.encode(text.take(4000), "UTF-8")

private fun reviewDate(iso: String?): String? = runCatching {
    LocalDate.parse(iso!!.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", I18n.locale))
}.getOrNull()

private fun ratingColor(score: Double): Color = when {
    score >= 7.5 -> Color(0xFF2E7D32)
    score >= 5.0 -> Color(0xFFF57C00)
    else -> Color(0xFFC62828)
}
