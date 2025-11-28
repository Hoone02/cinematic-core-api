package org.example.hoon.cinematicCore.model.generator

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.ModelElement
import org.example.hoon.cinematicCore.model.domain.UVPoint
import org.example.hoon.cinematicCore.model.domain.Vec3
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.prefix
import kotlin.math.abs

class ElementJsonBuilder {

    fun buildElement(
        element: ModelElement,
        model: BlockbenchModel,
        bone: Bone,
        context: ScaleContext
    ): JsonObject {
        val elementJson = JsonObject()

        val (inflatedFrom, inflatedTo) = if (element.inflate != 0.0) {
            applyInflate(element.from, element.to, element.inflate)
        } else {
            element.from to element.to
        }

        val offsetFrom = translate(inflatedFrom, context.originOffset)
        val offsetTo = translate(inflatedTo, context.originOffset)

        val scaledFrom = scalePoint(offsetFrom, context.pivot, context.scale)
        val scaledTo = scalePoint(offsetTo, context.pivot, context.scale)

        elementJson.add("from", toJsonArray(scaledFrom))
        elementJson.add("to", toJsonArray(scaledTo))

        val facesJson = JsonObject()
        val defaultTextureId = getDefaultTextureId(model)
        element.faces.north?.let { facesJson.add("north", createFaceJson(it, resolveTextureId(it.textureId, defaultTextureId, model), model)) }
        element.faces.east?.let { facesJson.add("east", createFaceJson(it, resolveTextureId(it.textureId, defaultTextureId, model), model)) }
        element.faces.south?.let { facesJson.add("south", createFaceJson(it, resolveTextureId(it.textureId, defaultTextureId, model), model)) }
        element.faces.west?.let { facesJson.add("west", createFaceJson(it, resolveTextureId(it.textureId, defaultTextureId, model), model)) }
        element.faces.up?.let { facesJson.add("up", createFaceJson(it, resolveTextureId(it.textureId, defaultTextureId, model), model)) }
        element.faces.down?.let { facesJson.add("down", createFaceJson(it, resolveTextureId(it.textureId, defaultTextureId, model), model)) }
        elementJson.add("faces", facesJson)

        val hasElementRotation = element.rotation.x != 0.0 || element.rotation.y != 0.0 || element.rotation.z != 0.0
        val hasBoneRotation = bone.rotation.x != 0.0 || bone.rotation.y != 0.0 || bone.rotation.z != 0.0

        if (hasElementRotation) {
            val rotationJson = JsonObject()
            val offsetOrigin = translate(element.origin, context.originOffset)
            val scaledOrigin = scalePoint(offsetOrigin, context.pivot, context.scale)
            rotationJson.add("origin", toJsonArray(scaledOrigin))
            resolveElementAxisAngle(element.rotation)?.let { axisAngle ->
                rotationJson.addProperty("axis", axisAngle.axis)
                rotationJson.addProperty("angle", axisAngle.angle)
                elementJson.add("rotation", rotationJson)
            }
        } else if (hasBoneRotation) {
            // Bone 회전은 JSON에서 제거하여 Minecraft 모델이 추가 회전을 적용하지 않도록 한다.
            // 추후 소환 시 ModelRotationRegistry를 통해 원본 Bone 회전을 재적용한다.
        }

        return elementJson
    }

    private fun translate(point: Vec3, offset: Vec3): Vec3 =
        Vec3(point.x + offset.x, point.y + offset.y, point.z + offset.z)

    private fun scalePoint(point: Vec3, pivot: Vec3, scale: Double): Vec3 {
        if (scale == 1.0) return point
        val scaledX = (point.x - pivot.x) * scale + pivot.x
        val scaledY = (point.y - pivot.y) * scale + pivot.y
        val scaledZ = (point.z - pivot.z) * scale + pivot.z
        return Vec3(scaledX, scaledY, scaledZ)
    }

    private fun toJsonArray(vec: Vec3): JsonArray = JsonArray().apply {
        add(vec.x)
        add(vec.y)
        add(vec.z)
    }

    private fun applyInflate(from: Vec3, to: Vec3, inflate: Double): Pair<Vec3, Vec3> {
        val newFrom = Vec3(
            from.x - inflate,
            from.y - inflate,
            from.z - inflate
        )
        val newTo = Vec3(
            to.x + inflate,
            to.y + inflate,
            to.z + inflate
        )
        return newFrom to newTo
    }

    private data class AxisAngle(val axis: String, val angle: Double)

    private fun resolveElementAxisAngle(rotation: Vec3): AxisAngle? {
        val epsilon = 1e-6
        val nonZeroAxes = mutableListOf<Pair<String, Double>>()
        if (abs(rotation.x) > epsilon) nonZeroAxes.add("x" to rotation.x)
        if (abs(rotation.y) > epsilon) nonZeroAxes.add("y" to rotation.y)
        if (abs(rotation.z) > epsilon) nonZeroAxes.add("z" to rotation.z)

        return when (nonZeroAxes.size) {
            0 -> null
            1 -> {
                val (axis, angle) = nonZeroAxes.first()
                AxisAngle(axis, angle)
            }
            else -> {
                ConsoleLogger.warn("${prefix} Cube 회전이 여러 축에 동시에 설정되어 있어 Minecraft JSON에서 표현할 수 없습니다: $rotation")
                null
            }
        }
    }

    private fun getDefaultTextureId(model: BlockbenchModel): String {
        // 모델에 실제로 존재하는 첫 번째 텍스처의 ID를 반환
        // 텍스처가 없으면 "0" 반환 (fallback)
        return model.textures.firstOrNull()?.id ?: "0"
    }

    private fun resolveTextureId(textureId: String?, defaultTextureId: String, model: BlockbenchModel): String {
        // textureId가 null이면 기본 텍스처 ID 사용
        if (textureId == null) {
            return defaultTextureId
        }
        
        // textureId가 실제로 존재하는 텍스처인지 확인
        val exists = model.textures.any { it.id == textureId || it.uuid == textureId }
        
        // 존재하지 않으면 기본 텍스처 ID 사용
        return if (exists) textureId else defaultTextureId
    }

    private fun createFaceJson(uvPoint: UVPoint, textureKey: String, model: BlockbenchModel): JsonObject {
        val faceJson = JsonObject()

        val texture = model.textures.find { it.id == textureKey || it.uuid == textureKey }
        val uvDivisor = texture?.width?.div(16.0) ?: 16.0

        val uvArray = JsonArray()
        uvArray.add(uvPoint.leftEdge / uvDivisor)
        uvArray.add(uvPoint.topEdge / uvDivisor)
        uvArray.add(uvPoint.rightEdge / uvDivisor)
        uvArray.add(uvPoint.bottomEdge / uvDivisor)

        faceJson.add("uv", uvArray)
        faceJson.addProperty("tintindex", 0)
        faceJson.addProperty("rotation", 0)
        faceJson.addProperty("texture", "#$textureKey")

        return faceJson
    }
}

