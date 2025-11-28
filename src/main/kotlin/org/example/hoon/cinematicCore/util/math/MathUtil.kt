package org.example.hoon.cinematicCore.util.math

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

object MathUtil {
    const val DEGREES_TO_RADIANS: Float = (Math.PI / 180.0).toFloat()
    const val MODEL_TO_BLOCK_MULTIPLIER: Float = 16f

    fun fma(a: Vector3f, b: Vector3f, c: Vector3f): Vector3f {
        a.x = fma(a.x, b.x, c.x)
        a.y = fma(a.y, b.y, c.y)
        a.z = fma(a.z, b.z, c.z)
        return a
    }

    fun fma(a: Vector3f, b: Float, c: Vector3f): Vector3f {
        a.x = fma(a.x, b, c.x)
        a.y = fma(a.y, b, c.y)
        a.z = fma(a.z, b, c.z)
        return a
    }

    fun fma(a: Float, b: Float, c: Float): Float {
        return Math.fma(a, b, c)
    }

    fun toQuaternion(vector: Vector3f): Quaternionf {
        return Quaternionf().rotateZYX(
            vector.z * DEGREES_TO_RADIANS,
            vector.y * DEGREES_TO_RADIANS,
            vector.x * DEGREES_TO_RADIANS
        )
    }

    /**
     * -180도에서 180도 범위의 각도를 0도에서 360도 범위로 변환합니다.
     * 
     * @param angle -180 ~ 180 범위의 각도
     * @return 0 ~ 360 범위의 각도 (0~180은 그대로, -180~-1은 180~359로 변환)
     */
    fun normalizeAngleTo360(angle: Float): Float {
        return if (angle >= 0f) {
            angle
        } else {
            360f + angle
        }
    }

    fun getHeightAndWidth(
        fromX: Double, fromY: Double, fromZ: Double,
        toX: Double, toY: Double, toZ: Double
    ): Pair<Double, Double> {
        val height = abs(toY - fromY)
        val widthX = abs(toX - fromX)
        val widthZ = abs(toZ - fromZ)
        val averageWidth = (widthX + widthZ) / 2.0

        return Pair(height, averageWidth)
    }
}

