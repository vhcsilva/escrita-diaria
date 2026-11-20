package com.escritadiaria.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Ground = Color(0xFFF6F6F3)
    val Ink = Color(0xFF161B26)
    val Muted = Color(0xFF5A6170)
    val Accent = Color(0xFF2F4FD8)
    val AccentSoft = Color(0xFFEEF1FD)
    val AccentText = Color(0xFF2A44B8)
    val Border = Color(0xFFD2D4CC)
    val CardBorder = Color(0xFFE2E3DE)
    val Divider = Color(0xFFECEDE8)
    val Track = Color(0xFFDCDDD7)
    val WarnBg = Color(0xFFFDF0E8)
    val WarnText = Color(0xFF8A3209)
    val WarnBorder = Color(0xFFE8B898)
    val Green = Color(0xFF1F6B3A)
    val Rule = Color(0xFFE6E9F2)
    val Stroke = Color(0xFF1A2440)
    val Highlight = Color(0xFFFFE58A)
    val Paper = Color.White
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Accent, onPrimary = Color.White,
            background = C.Ground, onBackground = C.Ink,
            surface = Color.White, onSurface = C.Ink,
            surfaceVariant = C.Divider, onSurfaceVariant = C.Muted,
            outline = C.Border, secondary = C.Ink,
        ),
        typography = Typography(),
        content = content,
    )
}

val TitleStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, lineHeight = 40.sp)

@Composable
fun Eyebrow(text: String, color: Color = C.Muted) {
    Text(text.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color)
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 24.dp,
    background: Color = Color.White,
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, if (background == Color.White) C.CardBorder else background, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Botão em formato de pílula com estado selecionado. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    height: Dp = 44.dp,
    fontSize: TextUnit = 15.sp,
    icon: ImageVector? = null,
) {
    val bg = when {
        selected && dark -> C.Ink
        selected -> C.AccentSoft
        else -> Color.White
    }
    val fg = when {
        selected && dark -> Color.White
        selected -> C.AccentText
        else -> C.Ink
    }
    val border = when {
        selected && dark -> C.Ink
        selected -> C.Accent
        else -> C.Border
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(height).defaultMinSize(minWidth = height).semantics { contentDescription = text },
        shape = RoundedCornerShape(height / 2),
        color = bg,
        border = BorderStroke(1.5.dp, border),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(text, color = fg, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    dark: Boolean = false,
    height: Dp = 56.dp,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val bg = when {
        !enabled -> C.Track
        dark -> C.Ink
        primary -> C.Accent
        else -> Color.White
    }
    val fg = if (primary || dark) Color.White else C.Ink
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = if (primary || dark) null else BorderStroke(1.5.dp, C.Border),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, dark: Boolean = false, size: Dp = 48.dp) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = if (dark) C.Ink else Color.White,
        border = if (dark) null else BorderStroke(1.5.dp, C.Border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = if (dark) Color.White else C.Ink, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack)
        Text(title, style = TitleStyle)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = C.Ink)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = C.Muted)
        }
        trailing()
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White),
    )
}

@Composable
fun Badge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg,
    )
}
