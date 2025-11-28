package org.example.hoon.cinematicCore.util.math

import org.joml.Vector3f

object InterpolationUtil {
    fun lerp(p0: Vector3f, p1: Vector3f, alpha: Float): Vector3f {
        return Vector3f(
            lerp(p0.x, p1.x, alpha),
            lerp(p0.y, p1.y, alpha),
            lerp(p0.z, p1.z, alpha)
        )
    }

    fun lerp(p0: Float, p1: Float, alpha: Float): Float {
        return MathUtil.fma(p1 - p0, alpha, p0)
    }

    fun catmullRom(p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f, t: Float): Vector3f {
        val t2 = t * t
        val t3 = t2 * t
        val result = Vector3f()

        result.x = MathUtil.fma(
            t3,
            MathUtil.fma(-1f, p0.x, MathUtil.fma(3f, p1.x, MathUtil.fma(-3f, p2.x, p3.x))),
            MathUtil.fma(
                t2,
                MathUtil.fma(2f, p0.x, MathUtil.fma(-5f, p1.x, MathUtil.fma(4f, p2.x, -p3.x))),
                MathUtil.fma(t, -p0.x + p2.x, 2f * p1.x)
            )
        )
        result.y = MathUtil.fma(
            t3,
            MathUtil.fma(-1f, p0.y, MathUtil.fma(3f, p1.y, MathUtil.fma(-3f, p2.y, p3.y))),
            MathUtil.fma(
                t2,
                MathUtil.fma(2f, p0.y, MathUtil.fma(-5f, p1.y, MathUtil.fma(4f, p2.y, -p3.y))),
                MathUtil.fma(t, -p0.y + p2.y, 2f * p1.y)
            )
        )
        result.z = MathUtil.fma(
            t3,
            MathUtil.fma(-1f, p0.z, MathUtil.fma(3f, p1.z, MathUtil.fma(-3f, p2.z, p3.z))),
            MathUtil.fma(
                t2,
                MathUtil.fma(2f, p0.z, MathUtil.fma(-5f, p1.z, MathUtil.fma(4f, p2.z, -p3.z))),
                MathUtil.fma(t, -p0.z + p2.z, 2f * p1.z)
            )
        )

        result.mul(0.5f)
        return result
    }
}

