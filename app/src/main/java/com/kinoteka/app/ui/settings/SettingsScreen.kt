package com.kinoteka.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinoteka.app.BuildConfig
import com.kinoteka.app.data.UiScale
import com.kinoteka.app.data.AppTheme
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kinoteka.app.data.reminders.Reminders
import com.kinoteka.app.ui.theme.JungleLeaves
import com.kinoteka.app.ui.theme.themeSwatch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.draw.clip
import com.kinoteka.app.data.Section
import com.kinoteka.app.data.SectionStyle
import com.kinoteka.app.ui.components.LocalSectionPrefs
import com.kinoteka.app.ui.components.SectionChips
import com.kinoteka.app.ui.components.icon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kinoteka.app.ui.LocalContainer
import com.kinoteka.app.i18n.AppLanguage
import com.kinoteka.app.i18n.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAccount: () -> Unit, onAbout: () -> Unit) {
    val container = LocalContainer.current
    val scale by container.ui.scale.collectAsStateWithLifecycle()
    val sync by container.sync.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr("Налаштування", "Settings", "Настройки")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ThemeCard()

                // Вигляд
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(tr("Розмір інтерфейсу", "Interface size", "Размер интерфейса"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            tr(
                                "Збільшує все одразу: текст, кнопки, меню, постери. Зміна видна відразу.",
                                "Scales everything at once: text, buttons, menus, posters. The change applies immediately.",
                                "Увеличивает всё сразу: текст, кнопки, меню, постеры. Изменение видно сразу.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            UiScale.entries.forEachIndexed { i, option ->
                                SegmentedButton(
                                    selected = option == scale,
                                    onClick = { container.ui.setScale(option) },
                                    shape = SegmentedButtonDefaults.itemShape(i, UiScale.entries.size),
                                    label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                }

                SectionStyleCard()
                SectionsCard()
                LanguageCard()
                RemindersCard()

                // Синхронізація и «Про застосунок»
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        NavRow(
                            icon = if (sync.signedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            title = tr("Google Диск", "Google Drive", "Google Диск"),
                            subtitle = sync.email ?: tr("Синхронізація вимкнена", "Sync is off", "Синхронизация выключена"),
                            onClick = onAccount,
                        )
                        NavRow(
                            icon = Icons.Default.Info,
                            title = tr("Про застосунок", "About", "О приложении"),
                            subtitle = tr("Версія ${BuildConfig.VERSION_NAME}, джерела даних", "Version ${BuildConfig.VERSION_NAME}, data sources", "Версия ${BuildConfig.VERSION_NAME}, источники данных"),
                            onClick = onAbout,
                        )
                    }
                }
            }
        }
    }
}

/** Выбор темы: четыре образца с цветами фона, карточки и акцента. */
@Composable
private fun ThemeCard() {
    val ui = LocalContainer.current.ui
    val current by ui.theme.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Тема", "Theme", "Тема"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { theme ->
                    val (bg, card, accent) = themeSwatch(theme, systemDark)
                    val selected = theme == current
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { ui.setTheme(theme) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            if (theme == AppTheme.JUNGLE) JungleLeaves(Modifier.fillMaxWidth().height(28.dp))
                            if (theme == AppTheme.SYSTEM) {
                                // Половина светлая, половина тёмная.
                                val (darkBg, _, _) = themeSwatch(AppTheme.DARK, true)
                                Box(Modifier.fillMaxSize().padding(start = 0.dp)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterEnd).background(darkBg))
                                }
                            }
                            Column(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth()) {
                                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(card))
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            theme.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Язык данных TMDB. Надписи приложения остаются украинскими. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageCard() {
    val container = LocalContainer.current
    val language by container.ui.appLanguage.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Мова застосунку", "App language", "Язык приложения"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                tr(
                    "Інтерфейс, назви фільмів, описи та жанри з TMDB. Якщо перекладу немає — показуємо англійський.",
                    "Interface, movie titles, descriptions and genres from TMDB. If there is no translation, English is shown.",
                    "Интерфейс, названия фильмов, описания и жанры из TMDB. Если перевода нет — показываем английский.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val options = AppLanguage.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, lang ->
                    SegmentedButton(
                        selected = lang == language,
                        onClick = {
                            if (lang != language) {
                                container.repository.clearCaches()
                                container.ui.setAppLanguage(lang)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        label = { Text(lang.label, maxLines = 1) },
                    )
                }
            }
        }
    }
}

/** Напоминания о новых сериях и премьерах (с запросом разрешения на уведомления). */
@Composable
private fun RemindersCard() {
    val context = LocalContext.current
    val ui = LocalContainer.current.ui
    val enabled by ui.reminders.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui.setReminders(granted)
        Reminders.schedule(context, granted)
        if (granted) Reminders.checkNow(context)
    }
    fun setEnabled(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ui.setReminders(on)
        Reminders.schedule(context, on)
        if (on) Reminders.checkNow(context)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Нагадування", "Reminders", "Напоминания"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = ::setEnabled)
            }
            Text(
                tr(
                    "Сповіщення, коли виходить нова серія серіалу зі статусом «Дивлюсь» (сьогодні або завтра) " +
                        "і коли виходить фільм із «Хочу подивитись». Перевірка двічі на день.",
                    "Notifications when a new episode of a show marked “Watching” comes out (today or tomorrow) " +
                        "and when a movie from “Want to watch” is released. Checked twice a day.",
                    "Уведомления, когда выходит новая серия сериала со статусом «Смотрю» (сегодня или завтра) " +
                        "и когда выходит фильм из «Хочу посмотреть». Проверка дважды в день.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                TextButton(onClick = { Reminders.checkNow(context) }) { Text(tr("Перевірити зараз", "Check now", "Проверить сейчас")) }
            }
        }
    }
}

/** Стиль кнопок разделов + живой предпросмотр. */
@Composable
private fun SectionStyleCard() {
    val ui = LocalContainer.current.ui
    val style by ui.sectionStyle.collectAsStateWithLifecycle()
    val visible = LocalSectionPrefs.current.visible
    var previewSelected by remember { mutableStateOf<Section?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Style, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Кнопки розділів", "Section buttons", "Кнопки разделов"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            SectionStyle.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = option == style, onClick = { ui.setSectionStyle(option) })
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == style, onClick = { ui.setSectionStyle(option) })
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                tr("Попередній перегляд", "Preview", "Предпросмотр"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            SectionChips(
                selected = previewSelected ?: visible.firstOrNull(),
                onSelect = { previewSelected = it },
                style = style,
            )
        }
    }
}

/** Какие разделы показывать и в каком порядке. */
@Composable
private fun SectionsCard() {
    val ui = LocalContainer.current.ui
    val order by ui.sectionOrder.collectAsStateWithLifecycle()
    val hidden by ui.hiddenSections.collectAsStateWithLifecycle()
    val visibleCount = order.count { it !in hidden }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Checklist, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Розділи", "Sections", "Разделы"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                tr("Позначте потрібні розділи та змініть порядок стрілками.", "Check the sections you need and reorder them with the arrows.", "Отметьте нужные разделы и измените порядок стрелками."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            order.forEachIndexed { index, section ->
                val isVisible = section !in hidden
                // Последний видимый раздел выключить нельзя.
                val canToggle = !isVisible || visibleCount > 1
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isVisible,
                        onCheckedChange = { ui.setSectionVisible(section, it) },
                        enabled = canToggle,
                    )
                    Icon(
                        section.icon, null, Modifier.size(22.dp),
                        tint = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        section.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { ui.moveSection(section, -1) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, tr("Вище", "Move up", "Выше"))
                    }
                    IconButton(onClick = { ui.moveSection(section, +1) }, enabled = index < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, tr("Нижче", "Move down", "Ниже"))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
