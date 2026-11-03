package com.chimafok.todo.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.chimafok.todo.data.CategoryEntity
import com.chimafok.todo.domain.*
import java.util.Locale

/** App-only locale. Never changes Locale.setDefault(), device language, stored names or instants. */
@android.annotation.SuppressLint("StaticFieldLeak") // Only Application context and its derived resources are retained.
object I18n {
    private var app: Context? = null
    @Volatile private var textContext: Pair<Locale, Context>? = null
    var choice by mutableStateOf(LanguageChoice.SYSTEM); private set
    private var systemLocales by mutableStateOf(listOf(Locale.ENGLISH))
    val locale: Locale get() = resolveLanguage(choice, systemLocales)
    fun initialize(context: Context, preference: String) { app = context.applicationContext; refreshSystem(); setChoice(preference) }
    fun setChoice(preference: String) { choice = LanguageChoice.valueOf(preference) }
    fun refreshSystem() {
        val list = Resources.getSystem().configuration.locales
        systemLocales = (0 until list.size()).map { list[it] }
    }
    // Preserve the Activity ContextWrapper chain for launchers, dialogs and back handlers.
    fun context(base: Context, selected: Locale = locale): Context = android.view.ContextThemeWrapper(base, base.theme).apply {
        applyOverrideConfiguration(Configuration(base.resources.configuration).apply { setLocale(resourceLocale(selected)) })
    }
    fun text(@StringRes id: Int, vararg args: Any?): String {
        val base = app
        return if (base != null) {
            val selected = locale
            val localized = textContext?.takeIf { it.first == selected }?.second
                ?: context(base, selected).also { textContext = selected to it }
            localized.getString(id, *args)
        }
        else String.format(Locale.ROOT, fallbackText(id), *args)
    }
}
fun tr(@StringRes id: Int, vararg args: Any?): String = I18n.text(id, *args)

@Composable fun LocalizedContent(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = I18n.locale
    val context = remember(base, configuration, locale) { I18n.context(base, locale) }
    val fonts = remember(context) { androidx.compose.ui.text.font.createFontFamilyResolver(context) }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalFontFamilyResolver provides fonts, LocalContext provides context, LocalConfiguration provides context.resources.configuration, androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr, content = content)
}

/** Translate only untouched built-in labels. A renamed category is user content. */
fun categoryName(category: CategoryEntity): String = when {
    category.id == UNCATEGORIZED_ID && category.name == "未分類" -> tr(com.chimafok.todo.R.string.category_uncategorized)
    category.id == ASSIGNMENTS_ID && category.name == "作業" -> tr(com.chimafok.todo.R.string.category_assignments)
    category.id == STUDY_ID && category.name == "學習" -> tr(com.chimafok.todo.R.string.category_study)
    category.id == "00000000-0000-4000-8000-000000000004" && category.name == "生活" -> tr(com.chimafok.todo.R.string.category_life)
    else -> category.name
}

fun languageName(code: String?): String = when(code) {
    "SYSTEM" -> tr(com.chimafok.todo.R.string.text_system_default_462fb4)
    "TRADITIONAL" -> "繁體中文"
    "SIMPLIFIED" -> "简体中文"
    "ENGLISH" -> "English"
    else -> tr(com.chimafok.todo.R.string.text_keep_local_value_856df0)
}
