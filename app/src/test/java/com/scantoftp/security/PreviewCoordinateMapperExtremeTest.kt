package com.scantoftp.security

import androidx.compose.ui.geometry.Rect
import com.scantoftp.ui.camera.mapFrameRectToView
import com.scantoftp.ui.camera.rotateNormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCoordinateMapperExtremeTest {

    @Test
    fun `rotateNormalizedRect handles extreme rotation degrees safely`() {
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)

        // 360 is normalized to 0 degrees, so output should match original rect exactly
        assertEquals(rect, rotateNormalizedRect(rect, 360))
        assertEquals(rect, rotateNormalizedRect(rect, 720))
        assertEquals(rect, rotateNormalizedRect(rect, -360))

        // Large positive and negative rotations should map to correct 90/180/270 degree equivalents
        val rotated90 = rotateNormalizedRect(rect, 90)
        assertEquals(rotated90, rotateNormalizedRect(rect, 450))   // 450 % 360 = 90
        assertEquals(rotated90, rotateNormalizedRect(rect, -270))  // -270 % 360 = 90

        val rotated180 = rotateNormalizedRect(rect, 180)
        assertEquals(rotated180, rotateNormalizedRect(rect, 540))  // 540 % 360 = 180
        assertEquals(rotated180, rotateNormalizedRect(rect, -180)) // -180 % 360 = 180

        val rotated270 = rotateNormalizedRect(rect, 270)
        assertEquals(rotated270, rotateNormalizedRect(rect, 630))  // 630 % 360 = 270
        assertEquals(rotated270, rotateNormalizedRect(rect, -90))  // -90 % 360 = 270
    }

    @Test
    fun `rotateNormalizedRect handles non-standard degrees gracefully`() {
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        // 45 degrees isn't 90/180/270, should fall through and return original rect
        val rotated45 = rotateNormalizedRect(rect, 45)
        assertEquals(rect, rotated45)
    }

    @Test
    fun `rotateNormalizedRect coerces points to safe unit interval`() {
        // Feed coordinates exceeding normal 0..1 bounding box range
        val rect = Rect(-1.5f, -0.5f, 2.5f, 3.0f)
        val rotated = rotateNormalizedRect(rect, 90)

        // Returned coordinates must be coerced within 0f and 1f boundary safely
        assertTrue(rotated.left >= 0f && rotated.left <= 1f)
        assertTrue(rotated.top >= 0f && rotated.top <= 1f)
        assertTrue(rotated.right >= 0f && rotated.right <= 1f)
        assertTrue(rotated.bottom >= 0f && rotated.bottom <= 1f)
    }

    @Test
    fun `mapFrameRectToView handles divide by zero and negative sizes`() {
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)

        // Passing 0 or negative values to frame/view sizes should return Rect.Zero, avoiding division-by-zero crashes
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 0, 100, 100f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 0, 100f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, 0f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, 100f, 0f))
        
        assertEquals(Rect.Zero, mapFrameRectToView(rect, -50, 100, 100f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, -100f, 100f))
    }

    @Test
    fun `mapFrameRectToView handles Float NaN and Infinity sizes gracefully`() {
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)

        // NaN or Infinity inputs should be caught by size verification and return Rect.Zero without crashing
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, Float.NaN, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, Float.POSITIVE_INFINITY, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, Float.NEGATIVE_INFINITY, 100f))
    }
}
