package com.example.mehanikpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================
// ТЁМНЫЕ ЦВЕТА (для контраста в цеху)
// ============================================================
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF121212),          // чёрный фон
    surface = Color(0xFF1E1E1E),             // тёмно-серый для карточек
    surfaceVariant = Color(0xFF2C2C2C),      // для карточек шагов
    primaryContainer = Color(0xFF3700B3),    // тёмно-фиолетовые кнопки (главный экран)
    secondaryContainer = Color(0xFF00695C),  // тёмно-зелёные (список машин)
    tertiaryContainer = Color(0xFFBF360C),   // тёмно-красные (список проблем)
    onBackground = Color.White,              // белый текст на фоне
    onSurface = Color.White,                 // белый текст на поверхностях
    onSurfaceVariant = Color(0xFFD3D3D3),    // светло-серый для второстепенного текста
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black
)

// ============================================================
// УВЕЛИЧЕННЫЙ ШРИФТ (для удобства чтения)
// ============================================================
val LargeTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    )
)

// ============================================================
// ТЕМА (используется в MainActivity)
// ============================================================
@Composable
fun MechanicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,  // всегда тёмная тема
        typography = LargeTypography,   // крупный шрифт
        content = content
    )
}