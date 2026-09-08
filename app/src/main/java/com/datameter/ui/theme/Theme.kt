package com.datameter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF046B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F2EA),
    onPrimaryContainer = Color(0xFF07352E),
    secondary = Color(0xFF365F8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EAFE),
    onSecondaryContainer = Color(0xFF102D49),
    tertiary = Color(0xFFA35C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4C7),
    onTertiaryContainer = Color(0xFF3C1F00),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF0E1714),
    surface = Color.White,
    onSurface = Color(0xFF0E1714),
    surfaceVariant = Color(0xFFE7EEEA),
    onSurfaceVariant = Color(0xFF394640),
    outline = Color(0xFF68756F),
    outlineVariant = Color(0xFFD8E1DD),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D8C7),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF085246),
    onPrimaryContainer = Color(0xFFD9F2EA),
    secondary = Color(0xFFA9C9F3),
    onSecondary = Color(0xFF0E314F),
    secondaryContainer = Color(0xFF244A72),
    onSecondaryContainer = Color(0xFFD9EAFE),
    tertiary = Color(0xFFFFBC78),
    onTertiary = Color(0xFF5A3000),
    tertiaryContainer = Color(0xFF7C4400),
    onTertiaryContainer = Color(0xFFFFE4C7),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF767676),
    onSurfaceVariant = Color(0xFFC7C7C7),
    outline = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFF4D4D4D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val DatameterShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

private val DatameterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun DatameterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DatameterTypography,
        shapes = DatameterShapes,
        content = content,
    )
}
