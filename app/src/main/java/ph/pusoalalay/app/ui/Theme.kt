package ph.pusoalalay.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Mint = Color(0xFF96D4C3)
val Cream = Color(0xFFF1EEE5)
val Muted = Color(0xFFBACAC3)
val Ink = Color(0xFF101B1B)
val Panel = Color(0xFF1C2B2A)
val Rose = Color(0xFFFFB4AB)

private val Colors = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF10372E), primaryContainer = Color(0xFF294B42),
    onPrimaryContainer = Cream, secondary = Color(0xFFDBC79E), onSecondary = Ink,
    background = Ink, onBackground = Cream, surface = Panel, onSurface = Cream,
    surfaceVariant = Color(0xFF263735), onSurfaceVariant = Muted,
    outline = Color(0xFF7E9790), error = Rose, onError = Color(0xFF690005)
)
@Composable
fun PusoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    ), content = content)
}
