package com.kinoteka.app.data

import android.content.Context
import com.kinoteka.app.i18n.AppLanguage
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Размер интерфейса: множитель для всего сразу (текст, кнопки, постеры). */
enum class UiScale(private val uk: String, private val en: String, private val ru: String, val factor: Float) {
    NORMAL("Звичайний", "Normal", "Обычный", 1.0f),
    LARGE("Великий", "Large", "Крупный", 1.15f),
    XLARGE("Дуже великий", "Extra large", "Очень крупный", 1.3f);

    val label: String get() = tr(uk, en, ru)
}

/** Тема оформления. */
enum class AppTheme(private val uk: String, private val en: String, private val ru: String) {
    SYSTEM("Системна", "System", "Системная"),
    LIGHT("Світла", "Light", "Светлая"),
    DARK("Темна", "Dark", "Тёмная"),
    JUNGLE("Джунглі", "Jungle", "Джунгли");

    val label: String get() = tr(uk, en, ru)
}

/** Как выглядят кнопки разделов (Фільми, Серіали…). */
enum class SectionStyle(private val uk: String, private val en: String, private val ru: String) {
    CHIPS("Таблетки", "Pills", "Таблетки"),
    CHIPS_ICONS("Таблетки з іконками", "Pills with icons", "Таблетки с иконками"),
    TABS("Вкладки", "Tabs", "Вкладки"),
    TILES("Плитки", "Tiles", "Плитки");

    val label: String get() = tr(uk, en, ru)
}

/** Настройки внешнего вида, хранятся на телефоне. */
class UiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _scale = MutableStateFlow(
        UiScale.entries.firstOrNull { it.name == prefs.getString(KEY_SCALE, null) } ?: UiScale.NORMAL
    )
    val scale: StateFlow<UiScale> = _scale.asStateFlow()

    private val _theme = MutableStateFlow(
        AppTheme.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: AppTheme.SYSTEM
    )
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(value: AppTheme) {
        _theme.value = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    private val _reminders = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, false))
    /** Напоминания о новых сериях и премьерах. */
    val reminders: StateFlow<Boolean> = _reminders.asStateFlow()

    fun setReminders(value: Boolean) {
        _reminders.value = value
        prefs.edit().putBoolean(KEY_REMINDERS, value).apply()
    }

    /** Язык приложения (интерфейс + данные TMDB). Старое «content_language» переносим. */
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString(KEY_APP_LANGUAGE, null))
            ?: AppLanguage.entries.firstOrNull { it.tmdb == prefs.getString(KEY_LANGUAGE, null) }
            ?: AppLanguage.UK
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _contentLanguage = MutableStateFlow(_appLanguage.value.tmdb)
    /** Язык данных TMDB: названия, описания, жанры (следует за языком приложения). */
    val contentLanguage: StateFlow<String> = _contentLanguage.asStateFlow()

    init {
        I18n.language = _appLanguage.value
    }

    fun setAppLanguage(value: AppLanguage) {
        _appLanguage.value = value
        I18n.language = value
        _contentLanguage.value = value.tmdb
        prefs.edit().putString(KEY_APP_LANGUAGE, value.code).apply()
    }

    private val _sectionStyle = MutableStateFlow(
        SectionStyle.entries.firstOrNull { it.name == prefs.getString(KEY_STYLE, null) } ?: SectionStyle.CHIPS
    )
    val sectionStyle: StateFlow<SectionStyle> = _sectionStyle.asStateFlow()

    /** Порядок всех разделов (включая скрытые). */
    private val _sectionOrder = MutableStateFlow(loadOrder())
    val sectionOrder: StateFlow<List<Section>> = _sectionOrder.asStateFlow()

    private val _hiddenSections = MutableStateFlow(loadHidden())
    val hiddenSections: StateFlow<Set<Section>> = _hiddenSections.asStateFlow()

    fun setScale(value: UiScale) {
        _scale.value = value
        prefs.edit().putString(KEY_SCALE, value.name).apply()
    }

    fun setSectionStyle(value: SectionStyle) {
        _sectionStyle.value = value
        prefs.edit().putString(KEY_STYLE, value.name).apply()
    }

    /** Показать/скрыть раздел. Последний видимый раздел скрыть нельзя. */
    fun setSectionVisible(section: Section, visible: Boolean) {
        val hidden = _hiddenSections.value.toMutableSet()
        if (visible) hidden -= section else hidden += section
        if (hidden.size >= Section.entries.size) return
        _hiddenSections.value = hidden
        prefs.edit().putString(KEY_HIDDEN, hidden.joinToString(",") { it.name }).apply()
    }

    /** Сдвинуть раздел вверх (-1) или вниз (+1) в списке. */
    fun moveSection(section: Section, delta: Int) {
        val order = _sectionOrder.value.toMutableList()
        val from = order.indexOf(section)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order.removeAt(from)
        order.add(to, section)
        _sectionOrder.value = order
        prefs.edit().putString(KEY_ORDER, order.joinToString(",") { it.name }).apply()
    }

    /**
     * Скрытые разделы. Дополнительные подборки, которых пользователь ещё не видел
     * (новые в этой версии), по умолчанию скрыты; основные — показаны.
     */
    private fun loadHidden(): Set<Section> {
        val hidden = prefs.getString(KEY_HIDDEN, "").orEmpty().split(',').mapNotNull { Section.fromName(it) }.toMutableSet()
        val known = prefs.getString(KEY_KNOWN, null)?.split(',')?.mapNotNull { Section.fromName(it) }?.toSet()
            ?: Section.entries.filter { it.isBase }.toSet() // до этой версии были только основные
        val newOnes = Section.entries.filter { it !in known }
        hidden += newOnes.filter { !it.isBase }
        prefs.edit()
            .putString(KEY_HIDDEN, hidden.joinToString(",") { it.name })
            .putString(KEY_KNOWN, Section.entries.joinToString(",") { it.name })
            .apply()
        return hidden
    }

    private fun loadOrder(): List<Section> {
        val saved = prefs.getString(KEY_ORDER, null)?.split(',')?.mapNotNull { Section.fromName(it) }.orEmpty()
        // Новые разделы (если появятся в будущих версиях) добавляются в конец.
        return (saved + Section.entries).distinct()
    }

    private companion object {
        const val KEY_SCALE = "scale"
        const val KEY_THEME = "theme"
        const val KEY_REMINDERS = "reminders"
        const val KEY_LANGUAGE = "content_language"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_STYLE = "section_style"
        const val KEY_ORDER = "section_order"
        const val KEY_HIDDEN = "section_hidden"
        const val KEY_KNOWN = "section_known"
    }
}
