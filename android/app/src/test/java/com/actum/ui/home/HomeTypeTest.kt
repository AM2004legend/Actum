package com.actum.ui.home

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.actum.ui.theme.Bone
import com.actum.ui.theme.InkOutline
import com.actum.ui.theme.Mono
import com.actum.ui.theme.Sans
import com.actum.ui.theme.Serif
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for typography helpers in [HomeType.kt].
 */
class HomeTypeTest {

    @Test
    fun serifHelperConfiguresYoungSerifWithLineHeightMultiple() {
        val style = serif(22.sp)
        assertEquals(Serif, style.fontFamily)
        assertEquals(FontWeight.Normal, style.fontWeight)
        assertEquals(22.sp, style.fontSize)
        assertEquals(Bone, style.color)
        assertEquals(22.sp * 1.15f, style.lineHeight)
    }

    @Test
    fun sansHelperConfiguresInstrumentSans() {
        val style = sans(16.sp, weight = FontWeight.Medium)
        assertEquals(Sans, style.fontFamily)
        assertEquals(FontWeight.Medium, style.fontWeight)
        assertEquals(16.sp, style.fontSize)
        assertEquals(Bone, style.color)
    }

    @Test
    fun monoStyleHelperConfiguresIbmPlexMono() {
        val style = monoStyle(11.sp, InkOutline, lineHeight = 18.sp)
        assertEquals(Mono, style.fontFamily)
        assertEquals(11.sp, style.fontSize)
        assertEquals(InkOutline, style.color)
        assertEquals(18.sp, style.lineHeight)
    }

    @Test
    fun metaStyleMatchesSpecification() {
        assertEquals(Mono, metaStyle.fontFamily)
        assertEquals(10.sp, metaStyle.fontSize)
        assertEquals(InkOutline, metaStyle.color)
        assertEquals(0.3.sp, metaStyle.letterSpacing)
    }
}
