package org.example.hoon.cinematicCore.core.util

import org.joml.Vector3f

/**
 * 수학 유틸리티
 * 순수 Kotlin 수학 함수들
 */
object MathUtils {
    /**
     * 선형 보간 (Lerp)
     */
    fun lerp(a: Vector3f, b: Vector3f, t: Float): Vector3f {
        val clampedT = t.coerceIn(0f, 1f)
        return Vector3f(a).lerp(b, clampedT)
    }
    
    /**
     * Catmull-Rom 스플라인 보간
     */
    fun catmullRom(p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f, t: Float): Vector3f {
        val t2 = t * t
        val t3 = t2 * t
        
        val result = Vector3f(p1).mul(2f)
        result.add(Vector3f(p0).mul(-1f))
        result.add(Vector3f(p2))
        result.mul(t3)
        
        val temp = Vector3f(p0).mul(2f)
        temp.add(Vector3f(p1).mul(-5f))
        temp.add(Vector3f(p2).mul(4f))
        temp.add(Vector3f(p3).mul(-1f))
        temp.mul(t2)
        result.add(temp)
        
        val temp2 = Vector3f(p0).mul(-1f)
        temp2.add(Vector3f(p2))
        temp2.mul(t)
        result.add(temp2)
        
        result.add(Vector3f(p1))
        result.mul(0.5f)
        
        return result
    }
}

