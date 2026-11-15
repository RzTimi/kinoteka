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

/** Настройки внешнего вида, хранятся на телефоне. */
class UiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _scale = MutableStateFlow(
        UiScale.entries.firstOrNull { it.name == prefs.getString(KEY_SCALE, null) } ?: UiScale.NORMAL
    )
    val scale: StateFlow<UiScale> = _scale.asStateFlow()

    fun setScale(value: UiScale) {
        _scale.value = value
        prefs.edit().putString(KEY_SCALE, value.name).apply()
    }

    private companion object {
        const val KEY_SCALE = "scale"
    }
}
