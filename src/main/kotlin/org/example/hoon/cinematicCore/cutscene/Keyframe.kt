package org.example.hoon.cinematicCore.cutscene

import org.bukkit.Location

/**
 * 보간 방식 열거형
 */
enum class InterpolationType {
    LINEAR,    // 선형 보간
    BEZIER     // 베지어 곡선 보간
}

/**
 * 컷씬의 키프레임 데이터 클래스
 * 
 * @param index 키프레임의 순서 (0부터 시작)
 * @param location 키프레임의 위치 (x, y, z, world)
 * @param pitch 수직 각도 (pitch)
 * @param yaw 수평 각도 (yaw)
 * @param interpolationType 이 키프레임에서 다음 키프레임까지의 보간 방식
 */
data class Keyframe(
    val index: Int,
    val location: Location,
    val pitch: Float,
    val yaw: Float,
    val interpolationType: InterpolationType = InterpolationType.LINEAR,
    val durationSeconds: Double? = null,
    val pauseSeconds: Double? = null,
    val startSmooth: Boolean = false,
    val endSmooth: Boolean = false
    // 향후 확장: roll (기울기) 추가 예정
)

