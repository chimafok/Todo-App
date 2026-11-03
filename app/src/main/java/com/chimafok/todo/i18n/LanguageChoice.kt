package com.chimafok.todo.i18n

import java.util.Locale

enum class LanguageChoice(val tag: String) { SYSTEM(""), TRADITIONAL("zh-Hant"), SIMPLIFIED("zh-Hans"), ENGLISH("en") }

fun resolveLanguage(choice: LanguageChoice, system: List<Locale>): Locale {
    if (choice != LanguageChoice.SYSTEM) return Locale.forLanguageTag(choice.tag)
    for (locale in system) {
        if (locale.language == "en") return Locale.ENGLISH
        if (locale.language == "zh") return Locale.forLanguageTag(
            if (locale.script == "Hant" || (locale.script != "Hans" && locale.country in listOf("TW", "HK", "MO"))) "zh-Hant" else "zh-Hans")
    }
    return Locale.ENGLISH
}

/** Explicit regions keep older Android font fallback consistent with the selected Chinese script. */
fun resourceLocale(locale: Locale): Locale = when {
    locale.language == "zh" && locale.script == "Hant" -> Locale.forLanguageTag("zh-Hant-TW")
    locale.language == "zh" -> Locale.forLanguageTag("zh-Hans-CN")
    else -> locale
}
