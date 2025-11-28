package org.example.hoon.cinematicCore.application.animation

import org.example.hoon.cinematicCore.core.animation.AnimationEngine
import org.example.hoon.cinematicCore.core.animation.BoneTransform
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation
import org.example.hoon.cinematicCore.util.animation.AnimationController
import org.bukkit.plugin.Plugin
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * 기본 애니메이션 엔진 구현
 * AnimationController를 래핑하여 AnimationEngine 인터페이스 구현
 */
class DefaultAnimationEngine(
    private val plugin: Plugin,
    private val model: BlockbenchModel,
    private val controller: AnimationController
) : AnimationEngine {
    
    constructor(
        plugin: Plugin,
        model: BlockbenchModel,
        mapper: org.example.hoon.cinematicCore.util.animation.BoneDisplayMapper
    ) : this(
        plugin,
        model,
        AnimationController(plugin, model, mapper)
    )
    
    override fun playAnimation(animation: BlockbenchAnimation, speed: Float, interruptible: Boolean) {
        controller.playAnimation(animation, speed, interruptible)
    }
    
    override fun pauseAnimation() {
        controller.pauseAnimation()
    }
    
    override fun resumeAnimation() {
        controller.resumeAnimation()
    }
    
    override fun stopAnimation(resetPose: Boolean) {
        controller.stopAnimation(resetPose, true)
    }
    
    override fun isAnimationPlaying(): Boolean {
        return controller.isAnimationPlaying()
    }
    
    override fun currentAnimation(): BlockbenchAnimation? {
        return controller.currentAnimation()
    }
    
    override fun getBoneTransform(boneUuid: String): BoneTransform? {
        val transform = controller.getBoneTransform(boneUuid) ?: return null
        return BoneTransform(
            translation = Vector3f(transform.translation),
            rotation = Quaternionf(transform.rotation),
            scale = Vector3f(transform.scale)
        )
    }
    
    override fun setBoneRotation(boneUuid: String, rotation: Quaternionf) {
        controller.setBoneRotation(boneUuid, rotation)
    }
    
    override fun setBoneTranslation(boneUuid: String, translation: Vector3f) {
        controller.setBoneTranslation(boneUuid, translation)
    }
    
    override fun setBoneScale(boneUuid: String, scale: Vector3f) {
        controller.setBoneScale(boneUuid, scale)
    }
    
    override fun update(deltaSeconds: Float) {
        controller.tick(deltaSeconds)
    }
    
    override fun reset() {
        controller.reset()
    }
    
    /**
     * 내부 컨트롤러 접근 (호환성 유지)
     */
    fun getController(): AnimationController = controller
}

