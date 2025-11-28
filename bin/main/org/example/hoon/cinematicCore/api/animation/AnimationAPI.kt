package org.example.hoon.cinematicCore.api.animation

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation
import org.example.hoon.cinematicCore.model.service.EntityModelUtil

/**
 * 애니메이션 관련 기능을 제공하는 API 클래스
 */
object AnimationAPI {
    
    /**
     * 애니메이션을 재생합니다 (BlockbenchAnimation 객체로)
     * 
     * @param entity 모델이 적용된 엔티티
     * @param animation 재생할 애니메이션 객체
     * @param speed 재생 속도 (기본값: 1.0f, 1.0f = 정상 속도)
     * @param interruptible 애니메이션 중단 가능 여부 (기본값: true)
     * @return 재생 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val model = ModelAPI.getModel(entity)
     * val animation = AnimationAPI.findAnimation(model, "walk")
     * animation?.let {
     *     AnimationAPI.playAnimation(entity, it, speed = 1.5f)
     * }
     * ```
     */
    fun playAnimation(
        entity: Entity,
        animation: BlockbenchAnimation,
        speed: Float = 1f,
        interruptible: Boolean = true
    ): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        session.animationController.playAnimation(animation, speed, interruptible)
        return true
    }
    
    /**
     * 애니메이션을 재생합니다 (이름으로)
     * 
     * @param entity 모델이 적용된 엔티티
     * @param animationName 재생할 애니메이션 이름
     * @param speed 재생 속도 (기본값: 1.0f, 1.0f = 정상 속도)
     * @param interruptible 애니메이션 중단 가능 여부 (기본값: true)
     * @return 재생 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * AnimationAPI.playAnimationByName(entity, "walk", speed = 1.5f)
     * ```
     */
    fun playAnimationByName(
        entity: Entity,
        animationName: String,
        speed: Float = 1f,
        interruptible: Boolean = true
    ): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        session.animationController.playAnimationByName(animationName, speed, interruptible)
        return true
    }
    
    /**
     * 애니메이션을 정지합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param resetPose 포즈를 초기 상태로 리셋할지 여부 (기본값: true)
     * @return 정지 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * AnimationAPI.stopAnimation(entity, resetPose = true)
     * ```
     */
    fun stopAnimation(entity: Entity, resetPose: Boolean = true): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        session.animationController.stopAnimation(resetPose, applyUpdate = true)
        return true
    }
    
    /**
     * 애니메이션을 일시정지합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @return 일시정지 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * AnimationAPI.pauseAnimation(entity)
     * ```
     */
    fun pauseAnimation(entity: Entity): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        session.animationController.pauseAnimation()
        return true
    }
    
    /**
     * 일시정지된 애니메이션을 재개합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @return 재개 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * AnimationAPI.resumeAnimation(entity)
     * ```
     */
    fun resumeAnimation(entity: Entity): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        session.animationController.resumeAnimation()
        return true
    }
    
    /**
     * 특정 모델에서 애니메이션을 찾습니다
     * 
     * @param model 애니메이션을 찾을 모델
     * @param animationName 찾을 애니메이션 이름 (대소문자 무시)
     * @return 찾은 애니메이션 객체, 없으면 null
     * 
     * @example
     * ```kotlin
     * val model = ModelAPI.getModel(entity)
     * val animation = AnimationAPI.findAnimation(model, "walk")
     * animation?.let {
     *     println("애니메이션 길이: ${it.length}초")
     * }
     * ```
     */
    fun findAnimation(model: BlockbenchModel, animationName: String): BlockbenchAnimation? {
        return model.animations.firstOrNull {
            it.name.equals(animationName.trim(), ignoreCase = true)
        }
    }
    
    /**
     * 현재 재생 중인 애니메이션을 가져옵니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @return 현재 재생 중인 애니메이션, 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val currentAnimation = AnimationAPI.getCurrentAnimation(entity)
     * currentAnimation?.let {
     *     println("현재 재생 중: ${it.name}")
     * }
     * ```
     */
    fun getCurrentAnimation(entity: Entity): BlockbenchAnimation? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        
        return session.animationController.currentAnimation()
    }
    
    /**
     * 애니메이션이 재생 중인지 확인합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @return 재생 중이면 true, 아니면 false
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * if (AnimationAPI.isAnimationPlaying(entity)) {
     *     println("애니메이션 재생 중")
     * }
     * ```
     */
    fun isAnimationPlaying(entity: Entity): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        return session.animationController.isAnimationPlaying()
    }
}

