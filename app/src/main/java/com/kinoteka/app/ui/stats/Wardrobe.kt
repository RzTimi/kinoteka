package com.kinoteka.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.ui.details.palette

/**
 * «Гардероб змейки»: окрасы и аксессуары. Открываются достижениями и уровнями,
 * выбранный вид виден везде, где есть змейка (оценки, «Поділитися», питомец).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnakeWardrobe(progress: SnakeProgress, achievements: List<Achievement>) {
    val ui = LocalContainer.current.ui
    val skinName by ui.snakeSkin.collectAsStateWithLifecycle()
    val hatName by ui.snakeHat.collectAsStateWithLifecycle()
    // Подсказка «как открыть» для нажатой закрытой вещи.
    var hint by remember { mutableStateOf<String?>(null) }
    val openCount = SnakeSkin.entries.count { progress.isOpen(it.unlock) } + SnakeHat.entries.count { progress.isOpen(it.unlock) }
    val total = SnakeSkin.entries.size + SnakeHat.entries.size

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Гардероб змійки", "Snake wardrobe", "Гардероб змейки"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                tr("Відкрито $openCount з $total. Нові речі відкриваються досягненнями й рівнями.",
                    "$openCount of $total unlocked. New items unlock with achievements and levels.",
                    "Открыто $openCount из $total. Новые вещи открываются достижениями и уровнями."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(tr("Забарвлення", "Color", "Окрас"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnakeSkin.entries.forEach { skin ->
                    val open = progress.isOpen(skin.unlock)
                    FilterChip(
                        selected = skin.name == skinName,
                        onClick = { if (open) { ui.setSnakeSkin(skin.name); hint = null } else hint = "🔒 ${skin.label} — ${progress.hint(skin.unlock, achievements)}" },
                        label = { Text(if (open) skin.label else "🔒 ${skin.label}") },
                        leadingIcon = { SkinDot(skin) },
                        modifier = Modifier.alpha(if (open) 1f else 0.55f),
                    )
                }
            }

            Text(tr("Аксесуар", "Accessory", "Аксессуар"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnakeHat.entries.forEach { hat ->
                    val open = progress.isOpen(hat.unlock)
                    FilterChip(
                        selected = hat.name == hatName,
                        onClick = { if (open) { ui.setSnakeHat(hat.name); hint = null } else hint = "🔒 ${hat.label} — ${progress.hint(hat.unlock, achievements)}" },
                        label = { Text((if (open) "" else "🔒 ") + (if (hat.emoji.isNotEmpty()) "${hat.emoji} " else "") + hat.label) },
                        modifier = Modifier.alpha(if (open) 1f else 0.55f),
                    )
                }
            }
            hint?.let {
                Text(
                    tr("Як відкрити: ", "How to unlock: ", "Как открыть: ") + it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Кружок-образец окраса (радужная — градиентом). */
@Composable
private fun SkinDot(skin: SnakeSkin) {
    val p = skin.palette()
    val fill = if (p.rainbow) Modifier.background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
    else Modifier.background(p.body)
    Box(Modifier.size(18.dp).clip(CircleShape).then(fill).border(1.5.dp, p.outline, CircleShape))
}
