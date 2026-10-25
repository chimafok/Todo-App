package com.chimafok.todo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TodoColors = darkColorScheme(
    primary = Color(0xFF68DEEF),
    onPrimary = Color(0xFF002F38),
    primaryContainer = Color(0xFF103D4B),
    onPrimaryContainer = Color(0xFFC2F6FF),
    secondary = Color(0xFF9AE8CB),
    secondaryContainer = Color(0xFF163E37),
    onSecondaryContainer = Color(0xFFC0F9E5),
    tertiary = Color(0xFFC0B2FF),
    background = Color(0xFF080F1B),
    onBackground = Color(0xFFE8F1FA),
    surface = Color(0xFF111F30),
    surfaceDim = Color(0xFF080F1B),
    surfaceBright = Color(0xFF293D51),
    surfaceContainerLowest = Color(0xFF060C16),
    surfaceContainerLow = Color(0xFF101C2B),
    surfaceContainer = Color(0xFF162638),
    surfaceContainerHigh = Color(0xFF1C3043),
    surfaceContainerHighest = Color(0xFF263B50),
    onSurface = Color(0xFFE8F1FA),
    surfaceVariant = Color(0xFF203247),
    onSurfaceVariant = Color(0xFFA9BED0),
    outline = Color(0xFF6B8B9E),
    outlineVariant = Color(0xFF294456),
    error = Color(0xFFFF8A92),
)

private val TodoTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun TodoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TodoColors,
        typography = TodoTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
