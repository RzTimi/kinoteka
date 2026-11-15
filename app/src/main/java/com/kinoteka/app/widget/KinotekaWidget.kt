package com.kinoteka.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kinoteka.app.KinotekaApp
import com.kinoteka.app.MainActivity
import com.kinoteka.app.data.MediaType
import com.kinoteka.app.data.library.LibraryEntry
import com.kinoteka.app.data.library.WatchStatus
import com.kinoteka.app.i18n.tr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Виджет на рабочий стол: «Що подивитись сьогодні» — три фильма из «Хочу подивитись»
 * (каждый раз в случайном порядке) и сериалы, которые вы сейчас смотрите.
 */
class KinotekaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val library = (context.applicationContext as KinotekaApp).container.library
        withTimeoutOrNull(3_000) { library.loaded.first { it } }
        val entries = library.entries.value.values
        val want = entries.filter { it.status == WatchStatus.WANT }.shuffled().take(3)
        val watching = entries.filter { it.type == MediaType.TV && it.status == WatchStatus.WATCHING }
            .sortedByDescending { it.updatedAt }.take(2)
        val wantCount = entries.count { it.status == WatchStatus.WANT }
        provideContent { WidgetContent(want, watching, wantCount) }
    }

    @Composable
    private fun WidgetContent(want: List<LibraryEntry>, watching: List<LibraryEntry>, wantCount: Int) {
        val green = ColorProvider(Color(0xFFA5D86E))
        val text = ColorProvider(Color(0xFFE2EEDA))
        val dim = ColorProvider(Color(0xFFB7CBB1))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFF14261A)).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(tr("🐍 Що подивитись?", "🐍 What to watch?", "🐍 Что посмотреть?"), style = TextStyle(color = green, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            if (want.isEmpty() && watching.isEmpty()) {
                Text(tr("Додайте фільми в «Хочу подивитись» — і змійка підкаже, що ввімкнути.", "Add movies to “Want to watch” and the snake will suggest what to put on.", "Добавьте фильмы в «Хочу посмотреть» — и змейка подскажет, что включить."), style = TextStyle(color = dim, fontSize = 13.sp))
            }
            want.forEach { e ->
                Text("• ${e.displayTitle}" + (e.year?.let { " ($it)" } ?: ""), style = TextStyle(color = text, fontSize = 14.sp), maxLines = 1)
            }
            if (wantCount > want.size) {
                Text(tr("…і ще ${wantCount - want.size} у списку", "…and ${wantCount - want.size} more on the list", "…и ещё ${wantCount - want.size} в списке"), style = TextStyle(color = dim, fontSize = 12.sp))
            }
            if (watching.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                Text(tr("Дивлюсь зараз", "Watching now", "Смотрю сейчас"), style = TextStyle(color = green, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                watching.forEach { e ->
                    Text(
                        "• ${e.displayTitle} · ${e.watchedEpisodeCount} " + tr("сер.", "ep.", "сер."),
                        style = TextStyle(color = text, fontSize = 14.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class KinotekaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KinotekaWidget()
}
