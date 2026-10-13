package com.actum.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicMathTest {

    @Test
    fun `a picture tile is 190 dp until its picture has loaded`() {
        assertEquals(190f, MosaicMath.pictureTileHeightDp(170f, null), 0f)
        assertEquals(190f, MosaicMath.pictureTileHeightDp(170f, 0f), 0f)
    }

    @Test
    fun `a picture tile is as tall as its picture is, within 150 and 270`() {
        assertEquals(170f, MosaicMath.pictureTileHeightDp(170f, 1f), 0.001f)
        assertEquals(255f, MosaicMath.pictureTileHeightDp(170f, 1.5f), 0.001f)
    }

    @Test
    fun `a long screenshot is held to 270 and a wide one to 150`() {
        assertEquals(270f, MosaicMath.pictureTileHeightDp(170f, 2.22f), 0f)
        assertEquals(150f, MosaicMath.pictureTileHeightDp(170f, 0.56f), 0f)
    }
}
