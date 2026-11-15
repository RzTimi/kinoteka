package com.kinoteka.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Язык приложения: интерфейс и данные TMDB. */
enum class AppLanguage(val code: String, val tmdb: String, val label: String) {
    UK("uk", "uk-UA", "Українська"),
    EN("en", "en-US", "English"),
    RU("ru", "ru-RU", "Русский");

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Текущий язык. Это состояние Compose: экраны, которые читают [tr],
 * перерисуются сами при смене языка. Код вне Compose (уведомления, виджет) просто берёт текущее значение.
 */
object I18n {
    var language: AppLanguage by mutableStateOf(AppLanguage.UK)
    val locale: Locale get() = language.locale
}

/** Строка на текущем языке: tr("Фільми", "Movies", "Фильмы"). */
fun tr(uk: String, en: String, ru: String): String = when (I18n.language) {
    AppLanguage.UK -> uk
    AppLanguage.EN -> en
    AppLanguage.RU -> ru
}

/**
 * Число со склонением. Формы через «|»: украинский и русский — «один|несколько|много»,
 * английский — «one|many». Пример: plural(n, "серія|серії|серій", "episode|episodes", "серия|серии|серий").
 * Возвращает только слово, без числа.
 */
fun plural(n: Int, uk: String, en: String, ru: String): String {
    val forms = tr(uk, en, ru).split('|')
    if (I18n.language == AppLanguage.EN) {
        return if (n == 1) forms[0] else forms.getOrElse(1) { forms[0] }
    }
    val m10 = n % 10
    val m100 = n % 100
    val index = when {
        m10 == 1 && m100 != 11 -> 0
        m10 in 2..4 && m100 !in 12..14 -> 1
        else -> 2
    }
    return forms.getOrElse(index) { forms.last() }
}
