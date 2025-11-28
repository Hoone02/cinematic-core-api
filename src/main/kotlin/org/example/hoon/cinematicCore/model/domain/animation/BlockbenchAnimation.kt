package org.example.hoon.cinematicCore.model.domain.animation

import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Blockbench 애니메이션 전체 정보를 표현하는 데이터 클래스
 */
data class BlockbenchAnimation(
    val name: String,
    val length: Float,
    val loopMode: AnimationLoopMode,
    val boneAnimations: Map<String, BoneAnimation>,
    val eventKeyframes: List<EventKeyframe> = emptyList()
)

/**
 * 한 Bone에 대한 위치/회전/스케일 키프레임 모음
 */
data class BoneAnimation(
    val boneUuid: String,
    val boneName: String,
    val positionKeyframes: List<AnimationKeyframe> = emptyList(),
    val rotationKeyframes: List<RotationKeyframe> = emptyList(),
    val scaleKeyframes: List<AnimationKeyframe> = emptyList()
)

/**
 * 키프레임 값과 보간 정보를 저장
 */
data class AnimationKeyframe(
    val time: Float,
    val value: Vector3f,
    val interpolation: KeyframeInterpolation,
    val bezier: BezierHandles? = null
)

data class RotationKeyframe(
    val time: Float,
    val euler: Vector3f,
    val quaternion: Quaternionf,
    val interpolation: KeyframeInterpolation,
    val handles: RotationBezierHandles? = null
)

data class RotationBezierHandles(
    val left: Vector3f,
    val right: Vector3f
)

/**
 * 베지어 보간 시 사용되는 좌/우 핸들 정보
 */
data class BezierHandles(
    val left: Vector3f,
    val right: Vector3f
)

/**
 * 애니메이션 타임라인 상의 커스텀 이벤트 키프레임
 */
data class EventKeyframe(
    val uuid: String,
    val time: Float,
    val script: String,
    val channel: String,
    val interpolation: KeyframeInterpolation = KeyframeInterpolation.LINEAR,
    val color: Int = -1
)

/**
 * 루프 모드
 */
enum class AnimationLoopMode {
    LOOP,
    HOLD,
    ONCE
}

/**
 * 키프레임 보간 타입
 */
enum class KeyframeInterpolation {
    LINEAR,
    CATMULL_ROM,
    BEZIER,
    STEP
}

