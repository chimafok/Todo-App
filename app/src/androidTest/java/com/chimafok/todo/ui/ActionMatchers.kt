package com.chimafok.todo.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription

/** Match an action's accessible label whether it is rendered as text or as an icon. */
fun hasActionLabel(label: String): SemanticsMatcher = hasText(label) or hasContentDescription(label)
