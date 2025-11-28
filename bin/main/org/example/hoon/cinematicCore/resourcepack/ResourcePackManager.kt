package org.example.hoon.cinematicCore.resourcepack

import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.CinematicCore
import java.io.File

class ResourcePackManager(private val plugin: Plugin) {
    private lateinit var resourcePackGenerator: ResourcePackGenerator

    /**
     * 필요한 디렉토리들을 생성하고 리소스팩 기본 구조 초기화
     * (폴더 구조, pack.mcmeta, blocks.json만 생성)
     */
    fun initialize() {
        // 플러그인 데이터 폴더 보장
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        // 플러그인 모델 폴더 생성
        val pluginFolder = File(plugin.dataFolder, "models")
        if (!pluginFolder.exists()) {
            pluginFolder.mkdirs()
        }
        
        // 리소스팩 생성기 초기화
        val serverPath = plugin.dataFolder.absolutePath
        resourcePackGenerator = ResourcePackGenerator(serverPath, plugin)
        resourcePackGenerator.createResourcePack()
    }
    
    /**
     * 커스텀 모델 데이터 등록 (leather_horse_armor.json 생성)
     * 모델 JSON 생성 후에 호출해야 함
     * 
     * @return Bone 정보와 custom_model_data 매핑 맵
     */
    fun registerCustomModelData(): Map<String, Int> {
        resourcePackGenerator.ensureBaseStructure()
        val modelsDir = File(CinematicCore.instance.dataFolder, "CC_Resource/assets/cinematiccore/models")
        return resourcePackGenerator.createLeatherHorseArmorJson(modelsDir)
        // 반환값 예: {"devil:head" -> 1, "devil:body" -> 2, ...}
    }
    
    /**
     * 리소스팩 생성기 반환
     */
    fun getResourcePackGenerator(): ResourcePackGenerator {
        if (!::resourcePackGenerator.isInitialized) {
            initialize()
        }
        return resourcePackGenerator
    }
}

