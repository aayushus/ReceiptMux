package com.scantoftp.ui.camera

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewCoordinateMapperTest {
    @Test
    fun `rotateNormalizedRect with 0 degrees returns original rect`() {
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        val rotated = rotateNormalizedRect(rect, 0)
        assertEquals(rect, rotated)
    }

    @Test
    fun `rotateNormalizedRect rotates 90 degrees correctly`() {
        // Points: (0.1, 0.2), (0.3, 0.2), (0.3, 0.4), (0.1, 0.4)
        // 90 deg rotation formula: (1 - y, x)
        // Points rotate to:
        // (1 - 0.2, 0.1) = (0.8, 0.1)
        // (1 - 0.2, 0.3) = (0.8, 0.3)
        // (1 - 0.4, 0.3) = (0.6, 0.3)
        // (1 - 0.4, 0.1) = (0.6, 0.1)
        // Min X = 0.6, Min Y = 0.1, Max X = 0.8, Max Y = 0.3
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        val rotated = rotateNormalizedRect(rect, 90)

        assertEquals(0.6f, rotated.left, 0.001f)
        assertEquals(0.1f, rotated.top, 0.001f)
        assertEquals(0.8f, rotated.right, 0.001f)
        assertEquals(0.3f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotateNormalizedRect rotates 180 degrees correctly`() {
        // 180 deg rotation formula: (1 - x, 1 - y)
        // Points rotate to:
        // (1 - 0.1, 1 - 0.2) = (0.9, 0.8)
        // (1 - 0.3, 1 - 0.2) = (0.7, 0.8)
        // (1 - 0.3, 1 - 0.4) = (0.7, 0.6)
        // (1 - 0.1, 1 - 0.4) = (0.9, 0.6)
        // Min X = 0.7, Min Y = 0.6, Max X = 0.9, Max Y = 0.8
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        val rotated = rotateNormalizedRect(rect, 180)

        assertEquals(0.7f, rotated.left, 0.001f)
        assertEquals(0.6f, rotated.top, 0.001f)
        assertEquals(0.9f, rotated.right, 0.001f)
        assertEquals(0.8f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotateNormalizedRect rotates 270 degrees correctly`() {
        // 270 deg rotation formula: (y, 1 - x)
        // Points rotate to:
        // (0.2, 1 - 0.1) = (0.2, 0.9)
        // (0.2, 1 - 0.3) = (0.2, 0.7)
        // (0.4, 1 - 0.3) = (0.4, 0.7)
        // (0.4, 1 - 0.1) = (0.4, 0.9)
        // Min X = 0.2, Min Y = 0.7, Max X = 0.4, Max Y = 0.9
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        val rotated = rotateNormalizedRect(rect, 270)

        assertEquals(0.2f, rotated.left, 0.001f)
        assertEquals(0.7f, rotated.top, 0.001f)
        assertEquals(0.4f, rotated.right, 0.001f)
        assertEquals(0.9f, rotated.bottom, 0.001f)
    }

    @Test
    fun `mapFrameRectToView returns zero rect when dimensions are invalid`() {
        val rect = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 0, 100, 100f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, -5, 100f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, 0f, 100f))
        assertEquals(Rect.Zero, mapFrameRectToView(rect, 100, 100, 100f, -10f))
    }

    @Test
    fun `mapFrameRectToView maps coordinates correctly`() {
        val rect = Rect(0.1f, 0.2f, 0.5f, 0.6f)
        // frame: 100x200
        // view: 200x400
        // scale: max(200/100, 400/200) = max(2f, 2f) = 2f
        // scaledWidth = 100 * 2 = 200
        // scaledHeight = 200 * 2 = 400
        // offsetX = (200 - 200) / 2 = 0
        // offsetY = (400 - 400) / 2 = 0
        // Expected left = 0 + 0.1 * 100 * 2 = 20f
        // Expected top = 0 + 0.2 * 200 * 2 = 80f
        // Expected right = 0 + 0.5 * 100 * 2 = 100f
        // Expected bottom = 0 + 0.6 * 200 * 2 = 240f
        val mapped = mapFrameRectToView(rect, 100, 200, 200f, 400f)

        assertEquals(20f, mapped.left, 0.001f)
        assertEquals(80f, mapped.top, 0.001f)
        assertEquals(100f, mapped.right, 0.001f)
        assertEquals(240f, mapped.bottom, 0.001f)
    }
}
