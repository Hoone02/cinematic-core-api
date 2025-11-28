package org.example.hoon.cinematicCore.core.animation

import org.example.hoon.cinematicCore.model.domain.animation.AnimationLoopMode
import org.example.hoon.cinematicCore.model.domain.animation.AnimationKeyframe
import org.example.hoon.cinematicCore.model.domain.animation.RotationKeyframe
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

/**
 * 애니메이션 타임라인 계산기
 * 키프레임 평가 및 보간을 담당
 */
interface AnimationTimeline {
    /**
     * 위치 키프레임 평가
     */
    fun evaluatePosition(
        keyframes: List<AnimationKeyframe>,
        timeSeconds: Float,
        loopMode: AnimationLoopMode,
        animationLength: Float
    ): Vector3f
    
    /**
     * 회전 키프레임 평가
     */
    fun evaluateRotation(
        keyframes: List<RotationKeyframe>,
        timeSeconds: Float,
        loopMode: AnimationLoopMode,
        animationLength: Float,
        initialRotation: Quaternionf
    ): Quaternionf?
    
    /**
     * 스케일 키프레임 평가
     */
    fun evaluateScale(
        keyframes: List<AnimationKeyframe>,
        timeSeconds: Float,
        loopMode: AnimationLoopMode,
        animationLength: Float
    ): Vector3f
}

