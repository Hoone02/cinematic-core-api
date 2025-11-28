package org.example.hoon.cinematicCore.model.store

import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.ModelElement
import org.example.hoon.cinematicCore.model.domain.Vec3
import java.util.concurrent.ConcurrentHashMap

/**
 * 모델 처리 과정에서 발견된 원본 rotation 데이터를 임시로 저장하는 레지스트리.
 * Minecraft JSON 생성 시 rotation을 0으로 내보내되, 실제 소환 시에는
 * 본래 Blockbench rotation을 다시 적용하기 위해 사용한다.
 */
object ModelRotationRegistry {
    data class BoneRotationInfo(
        val boneRotation: Vec3,
        val elementRotations: Map<String, Vec3>
    )

    private val rotationMap: MutableMap<String, MutableMap<String, BoneRotationInfo>> =
        ConcurrentHashMap()

    /**
     * 특정 모델/본에 대한 rotation 정보를 저장한다.
     *
     * @param modelName 모델 이름
     * @param bone 대상 Bone
     * @param childElements Bone에 속한 Element 목록
     */
    fun putBoneRotation(modelName: String, bone: Bone, childElements: List<ModelElement>) {
        val perModel = rotationMap.computeIfAbsent(modelName) { ConcurrentHashMap() }
        val elementRotations = childElements.associate { it.uuid to it.rotation }

        perModel[bone.uuid] = BoneRotationInfo(
            boneRotation = bone.rotation,
            elementRotations = elementRotations
        )
    }

    /**
     * 저장된 Bone rotation을 가져온다.
     *
     * @param modelName 모델 이름
     * @param boneUuid Bone UUID
     * @return Vec3 rotation 또는 null
     */
    fun getBoneRotation(modelName: String, boneUuid: String): Vec3? {
        return rotationMap[modelName]?.get(boneUuid)?.boneRotation
    }

    /**
     * Element rotation 목록을 가져온다.
     *
     * @param modelName 모델 이름
     * @param boneUuid Bone UUID
     * @return Element UUID -> Vec3 rotation 맵
     */
    fun getElementRotations(modelName: String, boneUuid: String): Map<String, Vec3>? {
        return rotationMap[modelName]?.get(boneUuid)?.elementRotations
    }

    /**
     * 특정 모델에 대한 데이터를 모두 제거한다.
     */
    fun clearModel(modelName: String) {
        rotationMap.remove(modelName)
    }

    /**
     * 전체 레지스트리를 비운다.
     */
    fun clearAll() {
        rotationMap.clear()
    }
}

