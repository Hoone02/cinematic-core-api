package org.example.hoon.cinematicCore.util.animation

import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Bone의 현재 변환 상태를 저장하는 데이터 클래스
 */
data class BoneTransform(
    val translation: Vector3f = Vector3f(0f, 0f, 0f),
    val rotation: Quaternionf = Quaternionf(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f)
) {
    /**
     * Bone의 초기 데이터로부터 BoneTransform을 생성
     */
    companion object {
        fun fromBone(bone: Bone, rotationOverride: Vec3? = null): BoneTransform {
            // Blockbench 좌표계를 Minecraft 좌표계로 변환
            // X축, Z축 반전
            val translation = Vector3f(
                -bone.origin.x.toFloat() / 16.0f,
                bone.origin.y.toFloat() / 16.0f,
                -bone.origin.z.toFloat() / 16.0f
            )
            
            // Blockbench rotation을 Quaternion으로 변환
            // Blockbench는 degrees 단위, ZYX 순서 (roll, pitch, yaw)
            // Minecraft는 radians 단위, ZYX 순서
            val rotation = Quaternionf()
            val rotationVec = rotationOverride ?: bone.rotation
            // Blockbench → Minecraft 좌표계 보정
            val xRad = Math.toRadians(-rotationVec.x)
            val yRad = Math.toRadians(rotationVec.y)
            val zRad = Math.toRadians(-rotationVec.z)

            // Blockbench uses Z (roll) -> Y (yaw) -> X (pitch) order
            rotation.rotateZ(zRad.toFloat())
                .rotateY(yRad.toFloat())
                .rotateX(xRad.toFloat())
            
            return BoneTransform(
                translation = translation,
                rotation = rotation,
                scale = Vector3f(1f, 1f, 1f)
            )
        }
    }
    
    /**
     * 로컬 변환 행렬을 생성
     * Forward Kinematics에서 사용: Translation -> Rotation -> Scale 순서
     * JOML Matrix4f의 메서드는 오른쪽에 행렬을 곱하므로:
     * .translate().rotate().scale()는 M = T * R * S (오른쪽에서 왼쪽으로 읽음)
     */
    fun toLocalMatrix(): org.joml.Matrix4f {
        // Forward Kinematics: Translation -> Rotation -> Scale
        // Matrix = T * R * S
        // JOML에서는 .translate().rotate().scale() 순서로 호출
        return org.joml.Matrix4f()
            .translate(translation)
            .rotate(rotation)
            .scale(scale)
    }
    
    /**
     * 복사본을 생성
     */
    fun copy(): BoneTransform {
        return BoneTransform(
            translation = Vector3f(translation),
            rotation = Quaternionf(rotation),
            scale = Vector3f(scale)
        )
    }
}

