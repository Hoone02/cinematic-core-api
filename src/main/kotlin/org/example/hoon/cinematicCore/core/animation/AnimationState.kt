package org.example.hoon.cinematicCore.core.animation

import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation

/**
 * 애니메이션 상태 관리
 */
data class AnimationState(
    var currentAnimation: BlockbenchAnimation? = null,
    var isPlaying: Boolean = false,
    var isFinished: Boolean = false,
    var timeSeconds: Float = 0f,
    var speed: Float = 1f,
    var isBlending: Boolean = false,
    var blendTime: Float = 0f,
    var blendDuration: Float = 0.3f,
    var blendFromAnimation: BlockbenchAnimation? = null,
    var blendFromTime: Float = 0f,
    var blendFromSpeed: Float = 1f
) {
    /**
     * 상태 리셋
     */
    fun reset() {
        currentAnimation = null
        isPlaying = false
        isFinished = false
        timeSeconds = 0f
        speed = 1f
        isBlending = false
        blendTime = 0f
        blendFromAnimation = null
        blendFromTime = 0f
        blendFromSpeed = 1f
    }
    
    /**
     * 새 애니메이션 시작
     */
    fun startAnimation(animation: BlockbenchAnimation, speed: Float, hadPrevious: Boolean) {
        blendFromAnimation = if (hadPrevious) currentAnimation else null
        blendFromTime = timeSeconds
        blendFromSpeed = this.speed
        
        currentAnimation = animation
        this.speed = speed
        timeSeconds = 0f
        isPlaying = true
        isFinished = false
        isBlending = hadPrevious
        blendTime = 0f
    }
}

