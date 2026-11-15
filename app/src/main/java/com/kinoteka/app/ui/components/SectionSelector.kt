package com.kinoteka.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.CameraRoll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.SectionStyle

/** Какие разделы показывать (в нужном порядке) и как выглядят их кнопки. */
data class SectionPrefs(
    val visible: List<Section> = Section.entries,
    val style: SectionStyle = SectionStyle.CHIPS,
)

val LocalSectionPrefs = compositionLocalOf { SectionPrefs() }

val Section.icon: ImageVector
    get() = when (this) {
        Section.MOVIES -> Icons.Default.Movie
        Section.SERIES -> Icons.Default.LiveTv
        Section.DORAMAS -> Icons.Default.LocalFlorist
        Section.ANIME -> Icons.Default.AutoAwesome
        Section.CARTOONS -> Icons.Default.ChildCare
        Section.DOCS -> Icons.Default.Public
        Section.TURKISH -> Icons.Default.Mosque
        Section.UKRAINIAN -> Icons.Default.Favorite
        Section.INDIAN -> Icons.Default.MusicNote
        Section.KOREAN_MOVIES -> Icons.Default.Theaters
        Section.REALITY -> Icons.Default.Groups
        Section.CLASSIC -> Icons.Default.CameraRoll
        Section.SHORTS -> Icons.Default.Timer
    }

/**
 * Переключатель разделов. Стиль и список видимых разделов берутся из настроек
 * ([LocalSectionPrefs]); [style] можно передать явно — для предпросмотра.
 */
@Composable
fun SectionChips(
    selected: Section?,
    onSelect: (Section?) -> Unit,
    modifier: Modifier = Modifier,
    showAll: Boolean = false,
    style: SectionStyle = LocalSectionPrefs.current.style,
    /** Только основные разделы (для поиска и «Моїх оцінок», где фильм относится к одному из них). */
    baseOnly: Boolean = false,
) {
    val sections = LocalSectionPrefs.current.visible.let { all -> if (baseOnly) all.filter { it.isBase } else all }
    // null в списке = кнопка «Усі».
    val options: List<Section?> = (if (showAll) listOf<Section?>(null) else emptyList()) + sections
    when (style) {
        SectionStyle.CHIPS, SectionStyle.CHIPS_ICONS -> LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it?.name ?: "all" }) { s ->
                FilterChip(
                    selected = selected == s,
                    onClick = { onSelect(s) },
                    label = { Text(s?.title ?: "Усі") },
                    leadingIcon = if (style == SectionStyle.CHIPS_ICONS) {
                        { Icon(s?.icon ?: Icons.Default.GridView, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }

        SectionStyle.TABS -> {
            val index = options.indexOf(selected).coerceAtLeast(0)
            PrimaryScrollableTabRow(
                selectedTabIndex = index,
                modifier = modifier.fillMaxWidth(),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
            ) {
                options.forEach { s ->
                    Tab(
                        selected = selected == s,
                        onClick = { onSelect(s) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(s?.title ?: "Усі", fontWeight = if (selected == s) FontWeight.SemiBold else FontWeight.Normal) },
                    )
                }
            }
        }

        SectionStyle.TILES -> LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(options, key = { it?.name ?: "all" }) { s ->
                val isSelected = selected == s
                Card(
                    onClick = { onSelect(s) },
                    modifier = Modifier.width(108.dp),
                    colors = if (isSelected) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            s?.icon ?: Icons.Default.GridView, null, Modifier.size(28.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s?.title ?: "Усі",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
