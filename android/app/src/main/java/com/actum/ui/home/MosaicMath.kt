package com.actum.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** Pure numbers behind the mosaic's tiles and its empty state. */
internal object MosaicMath {

    /** A picture tile's height before its picture has loaded. */
    const val PICTURE_DEFAULT_DP = 190f
    const val PICTURE_MIN_DP = 150f
    const val PICTURE_MAX_DP = 270f

    /**
     * A picture tile's height: its width times the picture's aspect (height over
     * width), kept between 150 and 270 dp, so a long screenshot does not run
     * down the page. 190 dp until the picture has loaded.
     */
    fun pictureTileHeightDp(widthDp: Float, aspect: Float?): Float =
        if (aspect == null || aspect <= 0f) PICTURE_DEFAULT_DP
        else (widthDp * aspect).coerceIn(PICTURE_MIN_DP, PICTURE_MAX_DP)
}
