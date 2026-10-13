package com.actum.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actum.R
import com.actum.ui.theme.AmberBright
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import com.actum.ui.common.Mark
import com.actum.ui.theme.Bone
import com.actum.ui.theme.InkOutline

/**
 * An empty mosaic says what to do, with the mark behind it, large and faint,
 * towards the corner where the "+" is.
 */
@Composable
internal fun NothingKept(modifier: Modifier = Modifier) {
    Box(modifier) {
        Mark(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 64.dp)
                .size(168.dp)
                .graphicsLayer { alpha = 0.09f },
            color = Bone,
        )
        Column(
            Modifier.padding(horizontal = 26.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.home_nothing_title), style = serif(22.sp))
            Text(stringResource(R.string.home_nothing_start), style = sans(15.sp, lineHeight = 22.sp))
            Text(stringResource(R.string.home_nothing_or), style = monoStyle(11.sp, InkOutline, lineHeight = 18.sp))
        }
    }
}
