package com.duckmail.tempmail

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0B57D0),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD3E3FD),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
    surface = androidx.compose.ui.graphics.Color(0xFFFEF7FF),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF3EFF7),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE6E0E9),
)

private val DarkScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA8C7FA),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF062E6F),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF0842A0),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF4A4458),
    surface = androidx.compose.ui.graphics.Color(0xFF141218),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF211F26),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF2B2930),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

@Composable
fun TempMailTheme(
    darkThemeSetting: Int = 0,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (darkThemeSetting) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
