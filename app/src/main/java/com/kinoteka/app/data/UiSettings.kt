package com.kinoteka.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Размер интерфейса: множитель для всего сразу (текст, кнопки, постеры). */
enum class UiScale(val label: String, val factor: Float) {
    NORMAL("Звичайний", 1.0f),
    LARGE("Великий", 1.15f),
    XLARGE("Дуже великий", 1.3f),
}

/** Как выглядят кнопки разделов (Фільми, Серіали…). */
enum class SectionStyle(val label: String) {
    CHIPS("Таблетки"),
    CHIPS_ICONS("Таблетки з іконками"),
    TABS("Вкладки"),
    TILES("Плитки"),
}

/** Настройки внешнего вида, хранятся на телефоне. */
class UiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _scale = MutableStateFlow(
        UiScale.entries.firstOrNull { it.name == prefs.getString(KEY_SCALE, null) } ?: UiScale.NORMAL
    )
    val scale: StateFlow<UiScale> = _scale.asStateFlow()

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
        const val KEY_STYLE = "section_style"
        const val KEY_ORDER = "section_order"
        const val KEY_HIDDEN = "section_hidden"
        const val KEY_KNOWN = "section_known"
    }
}
