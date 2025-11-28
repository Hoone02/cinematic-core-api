package org.example.hoon.cinematicCore.model.parser

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.example.hoon.cinematicCore.model.domain.*
import org.example.hoon.cinematicCore.model.domain.animation.AnimationKeyframe
import org.example.hoon.cinematicCore.model.domain.animation.AnimationLoopMode
import org.example.hoon.cinematicCore.model.domain.animation.BezierHandles
import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation
import org.example.hoon.cinematicCore.model.domain.animation.BoneAnimation
import org.example.hoon.cinematicCore.model.domain.animation.EventKeyframe
import org.example.hoon.cinematicCore.model.domain.animation.KeyframeInterpolation
import org.example.hoon.cinematicCore.model.domain.animation.RotationBezierHandles
import org.example.hoon.cinematicCore.model.domain.animation.RotationKeyframe
import org.example.hoon.cinematicCore.util.math.MathUtil
import org.joml.Vector3f

class BlockbenchModelParser {
    private val gson = Gson()

    fun parse(jsonString: String): BlockbenchModel {
        val jsonObject = JsonParser.parseString(jsonString).asJsonObject

        val modelName = jsonObject.get("name").asString
        val elementsArray = jsonObject.getAsJsonArray("elements")
        val outlinerArray = jsonObject.getAsJsonArray("outliner")

        val elements = elementsArray.map { elementJson ->
            parseElement(elementJson.asJsonObject)
        }

        // 모든 중첩된 bone을 평탄화하여 수집
        val bones = mutableListOf<Bone>()
        outlinerArray.forEach { outlinerElement ->
            if (outlinerElement.isJsonObject) {
                collectBones(outlinerElement.asJsonObject, bones)
            }
        }

        // 텍스처 파싱
        val textures = if (jsonObject.has("textures")) {
            jsonObject.getAsJsonArray("textures").map { textureJson ->
                parseTexture(textureJson.asJsonObject)
            }
        } else {
            emptyList()
        }

        val animations = if (jsonObject.has("animations")) {
            val animationsElement = jsonObject.get("animations")
            if (animationsElement.isJsonArray) {
                parseAnimations(animationsElement.asJsonArray, bones)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        return BlockbenchModel(modelName, elements, bones, textures, animations)
    }

    // 재귀적으로 모든 bone을 수집하는 함수
    private fun collectBones(boneJson: JsonObject, boneList: MutableList<Bone>) {
        // 현재 bone 파싱
        val bone = parseBone(boneJson)
        boneList.add(bone)

        // children이 있으면 재귀적으로 처리
        if (boneJson.has("children")) {
            val childrenArray = boneJson.getAsJsonArray("children")
            childrenArray.forEach { childElement ->
                if (childElement.isJsonObject) {
                    // 자식이 bone 객체인 경우 재귀 호출
                    collectBones(childElement.asJsonObject, boneList)
                }
                // 문자열(UUID)인 경우는 element 참조이므로 무시
            }
        }
    }

    private fun parseElement(element: JsonObject): ModelElement {
        val name = element.get("name").asString
        val uuid = element.get("uuid").asString

        val from = parseVec3(element.getAsJsonArray("from"))
        val to = parseVec3(element.getAsJsonArray("to"))

        val rotation = if (element.has("rotation")) {
            parseVec3(element.getAsJsonArray("rotation"))
        } else {
            Vec3(0.0, 0.0, 0.0)
        }

        val origin = parseVec3(element.getAsJsonArray("origin"))
        val faces = parseFaces(element.getAsJsonObject("faces"))
        
        val inflate = if (element.has("inflate")) {
            element.get("inflate").asDoubleOrZero()
        } else {
            0.0
        }

        return ModelElement(name, uuid, from, to, rotation, origin, faces, inflate)
    }

    private fun parseBone(bone: JsonObject): Bone {
        val name = bone.get("name").asString.lowercase()  // Bone 이름을 소문자로 변환
        val uuid = bone.get("uuid").asString
        val origin = parseVec3(bone.getAsJsonArray("origin"))

        val rotation = if (bone.has("rotation")) {
            parseVec3(bone.getAsJsonArray("rotation"))
        } else {
            Vec3(0.0, 0.0, 0.0)
        }

        // children UUID만 수집 (실제 Element UUID나 직접 자식 Bone의 UUID)
        val childrenArray = bone.getAsJsonArray("children")
        val children = childrenArray.mapNotNull { childElement ->
            when {
                childElement.isJsonPrimitive -> {
                    // 문자열(UUID)인 경우 - Element 참조
                    childElement.asString
                }
                childElement.isJsonObject -> {
                    // 중첩된 Bone 객체인 경우 해당 Bone의 UUID를 반환
                    val childObject = childElement.asJsonObject
                    if (childObject.has("uuid")) {
                        childObject.get("uuid").asString
                    } else {
                        null
                    }
                }
                else -> null
            }
        }

        // visibility 파싱 (기본값: true)
        val visibility = if (bone.has("visibility")) {
            bone.get("visibility").asBoolean
        } else {
            true
        }

        return Bone(name, uuid, origin, rotation, children, null, visibility)
    }

    private fun parseAnimations(array: com.google.gson.JsonArray, bones: List<Bone>): List<BlockbenchAnimation> {
        if (array.size() == 0) {
            return emptyList()
        }

        val boneIndexByUuid = bones.associateBy { it.uuid }
        val boneIndexByName = bones.associateBy { it.name }

        return array.mapNotNull { animationElement ->
            if (!animationElement.isJsonObject) {
                return@mapNotNull null
            }

            val animationObject = animationElement.asJsonObject
            val name = animationObject.get("name")?.asString ?: return@mapNotNull null
            val length = animationObject.get("length").asFloatOrZero()
            val loopMode = parseLoopMode(animationObject.get("loop")?.asString)

            val animators = if (animationObject.has("animators")) animationObject.getAsJsonObject("animators") else null
            val boneAnimations = mutableMapOf<String, BoneAnimation>()
            val eventKeyframes = mutableListOf<EventKeyframe>()

            if (animationObject.has("effects") && animationObject.get("effects").isJsonObject) {
                val effectsObject = animationObject.getAsJsonObject("effects")
                eventKeyframes.addAll(parseEventKeyframes(effectsObject))
            }

            animators?.entrySet()?.forEach { entry ->
                val key = entry.key
                val animatorObject = entry.value.asJsonObject
                val type = animatorObject.get("type")?.asString
                val typeLower = type?.lowercase()

                if (typeLower == "effect" || key.equals("effects", ignoreCase = true)) {
                    eventKeyframes.addAll(parseEventKeyframes(animatorObject))
                    return@forEach
                }

                if (typeLower != null && typeLower != "bone") {
                    return@forEach
                }

                val targetBone = boneIndexByUuid[key] ?: boneIndexByName[key.lowercase()]
                    ?: animatorObject.get("uuid")?.asString?.let { boneIndexByUuid[it] }
                    ?: animatorObject.get("name")?.asString?.lowercase()?.let { boneIndexByName[it] }
                if (targetBone == null) {
                    return@forEach
                }

                val keyframesArray = if (animatorObject.has("keyframes")) animatorObject.getAsJsonArray("keyframes") else null
                if (keyframesArray == null) {
                    return@forEach
                }

                val positionKeyframes = mutableListOf<AnimationKeyframe>()
                val rotationKeyframes = mutableListOf<RotationKeyframe>()
                val scaleKeyframes = mutableListOf<AnimationKeyframe>()

                keyframesArray.forEach { keyframeElement ->
                    if (!keyframeElement.isJsonObject) {
                        return@forEach
                    }
                    val keyframeObject = keyframeElement.asJsonObject
                    val channel = keyframeObject.get("channel")?.asString ?: return@forEach
                    val time = keyframeObject.get("time").asFloatOrZero()
                    val interpolation = parseInterpolation(keyframeObject.get("interpolation")?.asString)

                    val dataPointsArray = keyframeObject.getAsJsonArray("data_points")
                    val dataPoint = if (dataPointsArray != null && dataPointsArray.size() > 0) {
                        val element = dataPointsArray[0]
                        if (element.isJsonObject) element.asJsonObject else null
                    } else {
                        null
                    } ?: return@forEach
                    val value = parseVectorFromDataPoint(dataPoint)
                    val bezier = parseBezierHandles(dataPoint)

                    when (channel.lowercase()) {
                        "position", "pos", "offset" -> {
                            val keyframe = AnimationKeyframe(
                                time = time,
                                value = value,
                                interpolation = interpolation,
                                bezier = bezier
                            )
                            positionKeyframes.add(keyframe)
                        }
                        "rotation", "rot" -> {
                            val convertedValue = convertRotationToMinecraft(value)
                            val quaternion = MathUtil.toQuaternion(convertedValue)
                            val rotationHandles = bezier?.let {
                                RotationBezierHandles(
                                    left = convertRotationToMinecraft(it.left),
                                    right = convertRotationToMinecraft(it.right)
                                )
                            }
                            val rotationKeyframe = RotationKeyframe(
                                time = time,
                                euler = convertedValue,
                                quaternion = quaternion,
                                interpolation = interpolation,
                                handles = rotationHandles
                            )
                            rotationKeyframes.add(rotationKeyframe)
                        }
                        "scale", "scaling" -> {
                            val keyframe = AnimationKeyframe(
                                time = time,
                                value = value,
                                interpolation = interpolation,
                                bezier = bezier
                            )
                            scaleKeyframes.add(keyframe)
                        }
                    }
                }

                val boneAnimation = BoneAnimation(
                    boneUuid = targetBone.uuid,
                    boneName = targetBone.name,
                    positionKeyframes = positionKeyframes.sortedBy { it.time },
                    rotationKeyframes = rotationKeyframes.sortedBy { it.time },
                    scaleKeyframes = scaleKeyframes.sortedBy { it.time }
                )

                boneAnimations[targetBone.uuid] = boneAnimation
            }

            BlockbenchAnimation(
                name = name,
                length = length,
                loopMode = loopMode,
                boneAnimations = boneAnimations.toMap(),
                eventKeyframes = eventKeyframes.sortedBy { it.time }
            )
        }
    }

    private fun parseEventKeyframes(animatorObject: JsonObject): List<EventKeyframe> {
        val keyframesArray = animatorObject.getAsJsonArray("keyframes") ?: return emptyList()

        return keyframesArray.mapNotNull { keyframeElement ->
            if (!keyframeElement.isJsonObject) {
                return@mapNotNull null
            }
            val keyframeObject = keyframeElement.asJsonObject
            val time = keyframeObject.get("time").asFloatOrZero()
            val uuid = keyframeObject.get("uuid")?.asString ?: return@mapNotNull null
            val channel = keyframeObject.get("channel")?.asString ?: "timeline"
            val color = keyframeObject.get("color")?.asInt ?: -1
            val interpolation = parseInterpolation(keyframeObject.get("interpolation")?.asString)

            val dataPointsArray = keyframeObject.getAsJsonArray("data_points") ?: return@mapNotNull null
            val script = dataPointsArray.getOrNull(0)
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("script")
                ?.asString
                ?: return@mapNotNull null

            EventKeyframe(
                uuid = uuid,
                time = time,
                script = script,
                channel = channel,
                interpolation = interpolation,
                color = color
            )
        }
    }

    private fun parseLoopMode(loopValue: String?): AnimationLoopMode {
        return when (loopValue?.lowercase()) {
            "loop" -> AnimationLoopMode.LOOP
            "hold", "stay" -> AnimationLoopMode.HOLD
            "once", null -> AnimationLoopMode.ONCE
            else -> AnimationLoopMode.ONCE
        }
    }

    private fun parseInterpolation(value: String?): KeyframeInterpolation {
        return when (value?.lowercase()) {
            "linear" -> KeyframeInterpolation.LINEAR
            "catmullrom", "catmull_rom", "catmull" -> KeyframeInterpolation.CATMULL_ROM
            "bezier" -> KeyframeInterpolation.BEZIER
            "step", "instant" -> KeyframeInterpolation.STEP
            else -> KeyframeInterpolation.LINEAR
        }
    }

    private fun parseVectorFromDataPoint(dataPoint: JsonObject): Vector3f {
        val x = dataPoint.get("x").asFloatOrZero()
        val y = dataPoint.get("y").asFloatOrZero()
        val z = dataPoint.get("z").asFloatOrZero()
        return Vector3f(x, y, z)
    }

    private fun convertRotationToMinecraft(euler: Vector3f): Vector3f {
        return Vector3f(euler.x, -euler.y, -euler.z)
    }

    private fun parseBezierHandles(dataPoint: JsonObject): BezierHandles? {
        val leftObject = when {
            dataPoint.has("left") -> dataPoint.getAsJsonObject("left")
            dataPoint.has("left_value") -> dataPoint.getAsJsonObject("left_value")
            else -> null
        }
        val rightObject = when {
            dataPoint.has("right") -> dataPoint.getAsJsonObject("right")
            dataPoint.has("right_value") -> dataPoint.getAsJsonObject("right_value")
            else -> null
        }

        if (leftObject == null || rightObject == null) {
            return null
        }

        return BezierHandles(
            left = parseVectorFromDataPoint(leftObject),
            right = parseVectorFromDataPoint(rightObject)
        )
    }

    private fun com.google.gson.JsonArray.getOrNull(index: Int): com.google.gson.JsonElement? {
        return if (index in 0 until size()) this[index] else null
    }

    private fun com.google.gson.JsonElement?.asDoubleOrZero(): Double {
        if (this == null || this.isJsonNull) {
            return 0.0
        }
        if (!this.isJsonPrimitive) {
            return 0.0
        }
        val primitive = this.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asDouble
            primitive.isString -> primitive.asString.trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun com.google.gson.JsonElement?.asFloatOrZero(): Float {
        return this.asDoubleOrZero().toFloat()
    }

    private fun parseVec3(array: com.google.gson.JsonArray): Vec3 {
        return Vec3(
            x = array.getOrNull(0).asDoubleOrZero(),
            y = array.getOrNull(1).asDoubleOrZero(),
            z = array.getOrNull(2).asDoubleOrZero()
        )
    }

    private fun parseFaces(facesObj: JsonObject): Face {
        return Face(
            north = if (facesObj.has("north")) parseUVPoint(facesObj.getAsJsonObject("north")) else null,
            east = if (facesObj.has("east")) parseUVPoint(facesObj.getAsJsonObject("east")) else null,
            south = if (facesObj.has("south")) parseUVPoint(facesObj.getAsJsonObject("south")) else null,
            west = if (facesObj.has("west")) parseUVPoint(facesObj.getAsJsonObject("west")) else null,
            up = if (facesObj.has("up")) parseUVPoint(facesObj.getAsJsonObject("up")) else null,
            down = if (facesObj.has("down")) parseUVPoint(facesObj.getAsJsonObject("down")) else null
        )
    }

    private fun parseUVPoint(faceObj: JsonObject): UVPoint {
        val uvArray = faceObj.getAsJsonArray("uv")

        // Blockbench UV 배열: [첫번째, 두번째, 세번째, 네번째]
        // 첫번째: 가장 왼쪽면의 위치 (horizontal)
        // 두번째: 윗쪽면의 위치 (vertical)
        // 세번째: 가장 오른쪽면의 위치 (horizontal)
        // 네번째: 가장 아랫면의 위치 (vertical)
        val leftEdge = uvArray[0].asDouble    // 가장 왼쪽면의 위치
        val topEdge = uvArray[1].asDouble     // 윗쪽면의 위치
        val rightEdge = uvArray[2].asDouble   // 가장 오른쪽면의 위치
        val bottomEdge = uvArray[3].asDouble  // 가장 아랫면의 위치

        // 텍스처 ID 파싱 (있으면 가져오고 없으면 null)
        // Blockbench에서는 texture가 숫자이거나 문자열일 수 있음
        val textureId = if (faceObj.has("texture")) {
            val textureValue = faceObj.get("texture")
            if (textureValue.isJsonPrimitive) {
                // 숫자면 문자열로 변환, 이미 문자열이면 그대로 사용
                if (textureValue.asJsonPrimitive.isNumber) {
                    textureValue.asInt.toString()
                } else {
                    textureValue.asString
                }
            } else {
                null
            }
        } else {
            null
        }

        return UVPoint(
            leftEdge = leftEdge,
            topEdge = topEdge,
            rightEdge = rightEdge,
            bottomEdge = bottomEdge,
            textureId = textureId
        )
    }

    private fun parseTexture(texture: JsonObject): Texture {
        val name = texture.get("name").asString
        val id = texture.get("id").asString
        val uuid = texture.get("uuid").asString
        val width = texture.get("width").asInt
        val height = texture.get("height").asInt
        val source = texture.get("source").asString

        // 추가 필드들 파싱 (옵셔널 필드는 기본값 사용)
        val path = if (texture.has("path")) texture.get("path").asString else ""
        val folder = if (texture.has("folder")) texture.get("folder").asString else ""
        val namespace = if (texture.has("namespace")) texture.get("namespace").asString else ""
        val group = if (texture.has("group")) texture.get("group").asString else ""
        val uvWidth = if (texture.has("uv_width")) texture.get("uv_width").asInt else width
        val uvHeight = if (texture.has("uv_height")) texture.get("uv_height").asInt else height
        val particle = if (texture.has("particle")) texture.get("particle").asBoolean else false
        val useAsDefault = if (texture.has("use_as_default")) texture.get("use_as_default").asBoolean else false
        val layersEnabled = if (texture.has("layers_enabled")) texture.get("layers_enabled").asBoolean else false
        val syncToProject = if (texture.has("sync_to_project")) texture.get("sync_to_project").asString else ""
        val renderMode = if (texture.has("render_mode")) texture.get("render_mode").asString else "default"
        val renderSides = if (texture.has("render_sides")) texture.get("render_sides").asString else "auto"
        val pbrChannel = if (texture.has("pbr_channel")) texture.get("pbr_channel").asString else "color"
        val frameTime = if (texture.has("frame_time")) texture.get("frame_time").asInt else 1
        val frameOrderType = if (texture.has("frame_order_type")) texture.get("frame_order_type").asString else "loop"
        val frameOrder = if (texture.has("frame_order")) texture.get("frame_order").asString else ""
        val frameInterpolate = if (texture.has("frame_interpolate")) texture.get("frame_interpolate").asBoolean else false
        val visible = if (texture.has("visible")) texture.get("visible").asBoolean else true
        val internal = if (texture.has("internal")) texture.get("internal").asBoolean else false
        val saved = if (texture.has("saved")) texture.get("saved").asBoolean else false

        return Texture(
            name = name,
            id = id,
            uuid = uuid,
            width = width,
            height = height,
            source = source,
            path = path,
            folder = folder,
            namespace = namespace,
            group = group,
            uvWidth = uvWidth,
            uvHeight = uvHeight,
            particle = particle,
            useAsDefault = useAsDefault,
            layersEnabled = layersEnabled,
            syncToProject = syncToProject,
            renderMode = renderMode,
            renderSides = renderSides,
            pbrChannel = pbrChannel,
            frameTime = frameTime,
            frameOrderType = frameOrderType,
            frameOrder = frameOrder,
            frameInterpolate = frameInterpolate,
            visible = visible,
            internal = internal,
            saved = saved
        )
    }
}