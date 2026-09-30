package com.fixlens.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Native serif and humanist sans-serif remain available offline and scale with accessibility settings.
private val Editorial = FontFamily.Serif
private val Humanist = FontFamily(androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)))

val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Editorial, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Editorial, fontSize = 34.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = Editorial, fontSize = 29.sp, lineHeight = 35.sp),
    headlineSmall = TextStyle(fontFamily = Editorial, fontSize = 25.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = Editorial, fontSize = 23.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = Humanist, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Humanist, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Humanist, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Humanist, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Humanist, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Humanist, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Humanist, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Humanist, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp),
)
