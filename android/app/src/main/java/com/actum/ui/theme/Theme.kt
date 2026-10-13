package com.actum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material's scheme, filled from the palette's names, so a Material component
 * that nobody styled (a text field's cursor, a button's ripple) still belongs
 * to whichever ground is showing.
 *
 * Dynamic colour is deliberately off: letting the launcher wallpaper repaint
 * the app would dissolve the brand. The ground follows [Look], the person's
 * own choice, not the phone's setting.
 */
@Composable
fun ActumTheme(
    darkTheme: Boolean = Look.dark,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = AmberBright,
            onPrimary = AmberOnContainer,
            primaryContainer = AmberContainerDark,
            onPrimaryContainer = AmberContainerLight,
            secondary = BoneDim,
            onSecondary = Ink,
            background = Ink,
            onBackground = Bone,
            surface = Ink,
            onSurface = Bone,
            surfaceVariant = InkElevated,
            onSurfaceVariant = BoneDim,
            outline = InkOutline,
            outlineVariant = InkBorder,
        )
    } else {
        lightColorScheme(
            primary = AmberBright,
            onPrimary = AmberOnContainer,
            primaryContainer = AmberContainerLight,
            onPrimaryContainer = AmberDeep,
            secondary = BoneDim,
            onSecondary = Ink,
            background = Ink,
            onBackground = Bone,
            surface = Ink,
            onSurface = Bone,
            surfaceVariant = InkElevated,
            onSurfaceVariant = BoneDim,
            outline = InkOutline,
            outlineVariant = InkBorder,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
