package org.example.hoon.cinematicCore.core.animation

import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Forward Kinematics 엔진 인터페이스
 * Bone 계층 구조 변환 계산을 담당
 */
interface ForwardKinematicsEngine {
    /**
     * 모든 Bone의 변환을 계산하고 적용
     */
    fun update()
    
    /**
     * 특정 Bone의 변환 설정
     */
    fun setBoneTransform(boneUuid: String, transform: BoneTransform)
    
    /**
     * 특정 Bone의 변환 가져오기
     */
    fun getBoneTransform(boneUuid: String): BoneTransform?
    
    /**
     * 모든 Bone의 Y축 이동 조정
     */
    fun adjustAllBoneTranslationsY(yOffset: Float)
}

