package com.tahsinultimate.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.tahsinultimate.R

val QuranFont = FontFamily(Font(R.font.amiri_quran))

val MushafPageBg = Color(0xFFFFFDF3)
val MushafInk = Color(0xFF1A1A1A)
val MushafGold = Color(0xFF8D6E00)

@Composable
fun mushafStyle(size: Int = 24): TextStyle =
    LocalTextStyle.current.copy(
        fontFamily = QuranFont,
        fontSize = size.sp,
        lineHeight = (size * 2).sp,
        textDirection = TextDirection.Rtl,
        color = MushafInk,
    )

fun toArabicDigits(n: Int): String {
    val ar = "٠١٢٣٤٥٦٧٨٩"
    return n.toString().map { c -> if (c.isDigit()) ar[c - '0'] else c }.joinToString("")
}
