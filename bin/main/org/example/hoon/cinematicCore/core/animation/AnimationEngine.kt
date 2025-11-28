package org.example.hoon.cinematicCore.core.animation

import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * 애니메이션 엔진 인터페이스
 * 애니메이션 상태 관리 및 변환 계산을 담당하는 코어 인터페이스
 */
interface AnimationEngine {
    /**
     * 애니메이션 재생
     * @param animation 재생할 애니메이션
     * @param speed 재생 속도
     * @param interruptible 끊겨도 되는 애니메이션인지 여부 (false면 다른 애니메이션이 재생되어도 끝까지 재생)
     */
    fun playAnimation(animation: BlockbenchAnimation, speed: Float = 1f, interruptible: Boolean = true)
    
    /**
     * 애니메이션 일시정지
     */
    fun pauseAnimation()
    
    /**
     * 애니메이션 재개
     */
    fun resumeAnimation()
    
    /**
     * 애니메이션 정지
     * @param resetPose 초기 포즈로 리셋할지 여부
     */
    fun stopAnimation(resetPose: Boolean = true)
    
    /**
     * 애니메이션 재생 중인지 확인
     */
    fun isAnimationPlaying(): Boolean
    
    /**
     * 현재 재생 중인 애니메이션
     */
    fun currentAnimation(): BlockbenchAnimation?
    
    /**
     * Bone 변환 가져오기
     * @param boneUuid Bone UUID
     * @return Bone 변환, 없으면 null
     */
    fun getBoneTransform(boneUuid: String): BoneTransform?
    
    /**
     * Bone 회전 설정
     * @param boneUuid Bone UUID
     * @param rotation 회전 쿼터니언
     */
    fun setBoneRotation(boneUuid: String, rotation: Quaternionf)
    
    /**
     * Bone 이동 설정
     * @param boneUuid Bone UUID
     * @param translation 이동 벡터
     */
    fun setBoneTranslation(boneUuid: String, translation: Vector3f)
    
    /**
     * Bone 스케일 설정
     * @param boneUuid Bone UUID
     * @param scale 스케일 벡터
     */
    fun setBoneScale(boneUuid: String, scale: Vector3f)
    
    /**
     * 애니메이션 업데이트 (델타 시간 기반)
     * @param deltaSeconds 경과 시간 (초)
     */
    fun update(deltaSeconds: Float)
    
    /**
     * 리셋
     */
    fun reset()
}

/**
 * Bone 변환 데이터
 */
data class BoneTransform(
    val translation: Vector3f = Vector3f(0f, 0f, 0f),
    val rotation: Quaternionf = Quaternionf(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f)
)

