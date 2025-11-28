package org.example.hoon.cinematicCore.cutscene

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * 컷씬 생성, 로드, 저장을 관리하는 클래스
 */
class CutsceneManager(private val plugin: Plugin) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val sceneDirectory: File
    
    init {
        sceneDirectory = File(plugin.dataFolder, "scene")
        if (!sceneDirectory.exists()) {
            sceneDirectory.mkdirs()
        }
    }
    
    /**
     * 컷씬을 생성하고 저장
     * 
     * @param cutscene 저장할 컷씬
     * @return 저장 성공 여부
     */
    fun save(cutscene: Cutscene): Boolean {
        return try {
            val file = File(sceneDirectory, "${cutscene.name}.json")
            val json = cutsceneToJson(cutscene)
            file.writeText(gson.toJson(json))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 컷씬을 로드
     * 
     * @param sceneName 로드할 컷씬 이름
     * @return 로드된 컷씬, 실패 시 null
     */
    fun load(sceneName: String): Cutscene? {
        return try {
            val file = File(sceneDirectory, "$sceneName.json")
            if (!file.exists()) {
                return null
            }
            
            val jsonString = file.readText()
            val json = gson.fromJson(jsonString, JsonObject::class.java)
            jsonToCutscene(json)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 컷씬 삭제
     * 
     * @param sceneName 삭제할 컷씬 이름
     * @return 삭제 성공 여부
     */
    fun delete(sceneName: String): Boolean {
        return try {
            val file = File(sceneDirectory, "$sceneName.json")
            if (!file.exists()) {
                return false
            }
            
            return file.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 모든 컷씬 목록 반환
     * 
     * @return 컷씬 이름 리스트
     */
    fun list(): List<String> {
        return sceneDirectory.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
    }
    
    /**
     * 컷씬이 존재하는지 확인
     * 
     * @param sceneName 확인할 컷씬 이름
     * @return 존재 여부
     */
    fun exists(sceneName: String): Boolean {
        val file = File(sceneDirectory, "$sceneName.json")
        return file.exists()
    }
    
    /**
     * Cutscene을 JSON으로 변환
     */
    private fun cutsceneToJson(cutscene: Cutscene): JsonObject {
        val json = JsonObject()
        json.addProperty("name", cutscene.name)
        
        val keyframesArray = JsonArray()
        cutscene.keyframes.forEach { keyframe ->
            val keyframeJson = JsonObject()
            keyframeJson.addProperty("index", keyframe.index)
            keyframeJson.addProperty("world", keyframe.location.world?.name ?: "")
            keyframeJson.addProperty("x", keyframe.location.x)
            keyframeJson.addProperty("y", keyframe.location.y)
            keyframeJson.addProperty("z", keyframe.location.z)
            keyframeJson.addProperty("pitch", keyframe.pitch)
            keyframeJson.addProperty("yaw", keyframe.yaw)
            keyframeJson.addProperty("interpolationType", keyframe.interpolationType.name)
            keyframe.durationSeconds?.let { keyframeJson.addProperty("durationSeconds", it) }
            keyframe.pauseSeconds?.let { keyframeJson.addProperty("pauseSeconds", it) }
            keyframeJson.addProperty("startSmooth", keyframe.startSmooth)
            keyframeJson.addProperty("endSmooth", keyframe.endSmooth)
            keyframesArray.add(keyframeJson)
        }
        
        json.add("keyframes", keyframesArray)
        return json
    }
    
    /**
     * JSON을 Cutscene으로 변환
     */
    private fun jsonToCutscene(json: JsonObject): Cutscene? {
        try {
            val name = json.get("name").asString
            val keyframesArray = json.getAsJsonArray("keyframes")
            
            val keyframes = keyframesArray.mapNotNull { element ->
                val keyframeJson = element.asJsonObject
                val index = keyframeJson.get("index").asInt
                val worldName = keyframeJson.get("world").asString
                val world: World? = Bukkit.getWorld(worldName)
                
                if (world == null) {
                    return@mapNotNull null
                }
                
                val x = keyframeJson.get("x").asDouble
                val y = keyframeJson.get("y").asDouble
                val z = keyframeJson.get("z").asDouble
                val pitch = keyframeJson.get("pitch").asFloat
                val yaw = keyframeJson.get("yaw").asFloat
                val durationSeconds = if (keyframeJson.has("durationSeconds")) {
                    keyframeJson.get("durationSeconds")?.asDouble
                } else {
                    null
                }
                val pauseSeconds = if (keyframeJson.has("pauseSeconds")) {
                    keyframeJson.get("pauseSeconds")?.asDouble
                } else {
                    null
                }
                // 하위 호환성: startSmooth와 endSmooth가 Double이면 Boolean으로 변환
                val startSmooth = if (keyframeJson.has("startSmooth")) {
                    val value = keyframeJson.get("startSmooth")
                    when {
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asDouble > 0.0
                        else -> false
                    }
                } else {
                    false
                }
                val endSmooth = if (keyframeJson.has("endSmooth")) {
                    val value = keyframeJson.get("endSmooth")
                    when {
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asDouble > 0.0
                        else -> false
                    }
                } else {
                    false
                }
                
                // 보간 방식 (하위 호환성을 위해 기본값은 LINEAR)
                val interpolationType = try {
                    val typeStr = keyframeJson.get("interpolationType")?.asString ?: "LINEAR"
                    InterpolationType.valueOf(typeStr)
                } catch (e: Exception) {
                    InterpolationType.LINEAR
                }
                
                val location = Location(world, x, y, z)
                location.pitch = pitch
                location.yaw = yaw
                
                Keyframe(index, location, pitch, yaw, interpolationType, durationSeconds, pauseSeconds, startSmooth, endSmooth)
            }.sortedBy { it.index }
            
            return Cutscene(name, keyframes)
        } catch (e: Exception) {
            return null
        }
    }
}

