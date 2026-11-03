package com.chimafok.todo.domain

import com.chimafok.todo.i18n.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class LanguageChoiceTest {
    @Test fun renderingLocalesKeepChineseScriptsAndRegionsExplicit() {
        assertEquals("zh-Hant-TW", resourceLocale(Locale.forLanguageTag("zh-Hant")).toLanguageTag())
        assertEquals("zh-Hans-CN", resourceLocale(Locale.forLanguageTag("zh-Hans")).toLanguageTag())
        assertEquals(Locale.ENGLISH, resourceLocale(Locale.ENGLISH))
    }
    @Test fun explicitChoicesOverrideSystem() {
        assertEquals("zh-Hant",resolveLanguage(LanguageChoice.TRADITIONAL,listOf(Locale.US)).toLanguageTag())
        assertEquals("zh-Hans",resolveLanguage(LanguageChoice.SIMPLIFIED,listOf(Locale.TAIWAN)).toLanguageTag())
        assertEquals("en",resolveLanguage(LanguageChoice.ENGLISH,listOf(Locale.CHINA)).toLanguageTag())
    }
    @Test fun chineseScriptAndRegionResolveCorrectly() {
        listOf("zh-TW","zh-HK","zh-MO","zh-Hant-CN").forEach { assertEquals("zh-Hant",resolveLanguage(LanguageChoice.SYSTEM,listOf(Locale.forLanguageTag(it))).toLanguageTag()) }
        listOf("zh-CN","zh-SG","zh","zh-Hans-HK").forEach { assertEquals("zh-Hans",resolveLanguage(LanguageChoice.SYSTEM,listOf(Locale.forLanguageTag(it))).toLanguageTag()) }
    }
    @Test fun followSystemUsesSupportedLanguageListThenEnglishFallback() {
        assertEquals("zh-Hant",resolveLanguage(LanguageChoice.SYSTEM,listOf(Locale.FRENCH,Locale.TAIWAN)).toLanguageTag())
        assertEquals("en",resolveLanguage(LanguageChoice.SYSTEM,listOf(Locale.FRENCH)).toLanguageTag())
        assertEquals("en",resolveLanguage(LanguageChoice.SYSTEM,emptyList()).toLanguageTag())
        assertEquals("en",resolveLanguage(LanguageChoice.SYSTEM,listOf(Locale.UK)).toLanguageTag())
    }
}
