package it.palsoftware.pastiera

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class T2eCornerCalibrationTest {
    @Test fun zeroSquircleIsCircleAndOffsetKeepsCenter() {
        for (offset in listOf(-4f, 0f, 4f)) {
            val config = T2eCornerCalibration(size = 1f, offsetPx = offset, squircle = 0f, shiftYPx = 0f)
            for (step in 0..90) {
                val p = T2eCornerGeometry.point(100f, 1200f, step * PI / 180, config)
                assertEquals((100f - offset).toDouble(), hypot((p.x - 100).toDouble(), (p.y - 1100).toDouble()), 0.001)
            }
        }
    }

    @Test fun squircleOffsetHasConstantLengthAndIsNormalToCurve() {
        for (factor in listOf(0f, 1f, 4f)) {
            val base = T2eCornerCalibration(size = 1f, offsetPx = 0f, squircle = factor, shiftYPx = 0f)
            for (step in 5..85 step 5) {
                val angle = step * PI / 180
                val p = T2eCornerGeometry.point(100f, 1200f, angle, base)
                val q = T2eCornerGeometry.point(100f, 1200f, angle, base.copy(offsetPx = 3f))
                val a = T2eCornerGeometry.point(100f, 1200f, angle - 0.001, base)
                val b = T2eCornerGeometry.point(100f, 1200f, angle + 0.001, base)
                val dx = (q.x-p.x).toDouble()
                val dy = (q.y-p.y).toDouble()
                val tx = (b.x-a.x).toDouble()
                val ty = (b.y-a.y).toDouble()
                assertEquals(3.0, hypot(dx, dy), 0.001)
                assertEquals(0.0, (dx*tx+dy*ty)/hypot(tx, ty), 0.01)
            }
        }
    }

    @Test fun squircleFillsTheDiagonalAndInverseMatchesContour() {
        val circle = T2eCornerGeometry.point(100f, 1200f, PI/4, T2eCornerCalibration(size = 1f, offsetPx = 0f, squircle = 0f, shiftYPx = 0f))
        val config = T2eCornerCalibration(squircle = 2f)
        val p = T2eCornerGeometry.point(100f, 1200f, PI/4, config)
        assertTrue(p.x < circle.x)
        assertTrue(p.y > circle.y)
        val inverse = T2eCornerGeometry.atY(100f, 1200f, p.y, config)
        assertEquals(p.x.toDouble(), inverse.x.toDouble(), 0.001)
    }
}
