package org.example.hoon.cinematicCore.model.store

import org.example.hoon.cinematicCore.model.domain.Bone
import java.util.concurrent.ConcurrentHashMap

/**
 * 크기가 조정된 Bone 정보를 메모리에 캐시하는 전담 클래스.
 * JSON, 스케일 값과 함께 Bone을 저장해 재사용한다.
 */
class ScaledBoneCache {

    data class Entry(
        val bone: Bone,
        val json: String,
        val scale: Double,
        val modelName: String
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    fun put(modelName: String, bone: Bone, jsonContent: String, scale: Double) {
        cache[key(modelName, bone.name)] = Entry(bone, jsonContent, scale, modelName)
    }

    fun getBone(modelName: String, boneName: String): Bone? =
        cache[key(modelName, boneName)]?.bone

    fun getEntry(modelName: String, boneName: String): Entry? =
        cache[key(modelName, boneName)]

    fun getJson(modelName: String, boneName: String): String? =
        cache[key(modelName, boneName)]?.json

    fun getBonesByModel(modelName: String): List<Bone> =
        cache.values.filter { it.modelName == modelName }.map { it.bone }

    fun keys(): Set<String> = cache.keys

    fun clear() {
        cache.clear()
    }

    private fun key(modelName: String, boneName: String): String = "$modelName:$boneName"
}

