package org.example.hoon.cinematicCore.resourcepack

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.ChatColor
import org.example.hoon.cinematicCore.model.loader.BoneFileLoader
import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.getNamePart
import org.example.hoon.cinematicCore.util.prefix
import java.io.File
import java.net.JarURLConnection

class ResourcePackGenerator(private val serverPath: String, private val plugin: Plugin? = null) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val resourcePackName = "CC_Resource"
    private val resourcePackPath = File(serverPath, resourcePackName)
    
    /**
     * player 모델에서 player_head를 사용해야 하는 bone인지 확인
     */
    private fun isPlayerHeadBone(boneName: String): Boolean {
        return when (boneName.lowercase()) {
            "head",
            "right_arm",
            "left_arm",
            "torso",
            "right_leg",
            "left_leg",
            "right_forearm",
            "left_forearm",
            "waist",
            "lower_right_leg",
            "lower_left_leg" -> true
            else -> false
        }
    }
    
    /**
     * 리소스팩 폴더 구조 생성
     */
    fun createResourcePack() {
        ensureBaseStructure()
        copyResourcesMinecraftFolder()
        copyCamcorderResources()
        copyEnderPearlResource()
        ConsoleLogger.success("${prefix} 리소스팩 생성 완료!")
    }
    
    /**
     * 폴더 구조 생성
     */
    private fun createDirectoryStructure() {
        val directories = listOf(
            resourcePackPath,
            File(resourcePackPath, "assets/minecraft/atlases"),
            File(resourcePackPath, "assets/minecraft/models/item"),
            File(resourcePackPath, "assets/minecraft/items"),
            File(resourcePackPath, "assets/cinematiccore/models"),
            File(resourcePackPath, "assets/cinematiccore/models/camcorder"),
            File(resourcePackPath, "assets/cinematiccore/textures/entity"),
            File(resourcePackPath, "assets/cinematiccore/textures/entity/camcorder")
        )
        
        directories.forEach { dir ->
            if (dir.mkdirs()) {
//                println("폴더 생성: $path")
            } else if (dir.exists()) {
//                println("폴더 이미 존재: $path")
            } else {
//                println("폴더 생성 실패: $path")
            }
        }
    }
    
    /**
     * pack.mcmeta 파일 생성
     */
    private fun createPackMcmeta() {
        val packMcmeta = JsonObject()
        
        // features
        val features = JsonObject()
        val enabled = JsonArray()
        enabled.add("minecraft:minecart_improvements")
        features.add("enabled", enabled)
        packMcmeta.add("features", features)
        
        // pack
        val pack = JsonObject()
        val description = JsonObject()
        description.addProperty("translate", "Cinematic Core Resource")
        pack.add("description", description)
        pack.addProperty("pack_format", 64)
        packMcmeta.add("pack", pack)
        
        // 파일 저장
        val file = File(resourcePackPath, "pack.mcmeta")
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(packMcmeta))
    }
    
    /**
     * blocks.json 파일 생성
     */
    private fun createBlocksJson() {
        val blocksJson = JsonObject()
        val sources = JsonArray()
        
        val source = JsonObject()
        source.addProperty("source", "entity")
        source.addProperty("prefix", "entity/")
        source.addProperty("type", "directory")
        sources.add(source)
        
        blocksJson.add("sources", sources)
        
        // 파일 저장
        val file = File(resourcePackPath, "assets/minecraft/atlases/blocks.json")
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(blocksJson))
    }
    
    /**
     * 모델 JSON 파일을 리소스팩에 저장
     * @param modelName 모델 파일명 (확장자 제외)
     * @param jsonContent JSON 문자열
     */
    fun saveModel(modelName: String, jsonContent: String) {
        val modelFile = File(resourcePackPath, "assets/cinematiccore/models/$modelName.json")
        modelFile.writeText(jsonContent)
        ConsoleLogger.info("${prefix} 모델 저장: $modelName.json")
    }
    
    /**
     * 텍스처 파일을 리소스팩에 복사
     * @param sourcePath 원본 텍스처 파일 경로
     * @param textureName 저장될 텍스처 파일명
     */
    fun copyTexture(sourcePath: String, textureName: String) {
        val sourceFile = File(sourcePath)
        val targetFile = File(resourcePackPath, "assets/cinematiccore/textures/entity/$textureName")
        targetFile.parentFile?.mkdirs()
        
        if (sourceFile.exists()) {
            sourceFile.copyTo(targetFile, overwrite = true)
        } else {
            ConsoleLogger.warn("텍스처 파일을 찾을 수 없음: $sourcePath")
        }
    }
    
    /**
     * 리소스팩 경로 반환
     */
    fun getResourcePackPath(): String = resourcePackPath.absolutePath
    
    /**
     * 모델 폴더 경로 반환
     */
    fun getModelsPath(): String = File(resourcePackPath, "assets/cinematiccore/models").absolutePath
    
    /**
     * 텍스처 폴더 경로 반환
     */
    fun getTexturesPath(): String = File(resourcePackPath, "assets/cinematiccore/textures/entity").absolutePath
    
    /**
     * leather_horse_armor.json 파일 생성
     * 모든 모델의 모든 Bone을 overrides에 추가
     * 
     * @param modelsDirectory 모델들이 저장된 디렉토리 (예: CC_Resource/assets/cinematiccore/models)
     * @return Bone 정보와 custom_model_data 매핑 맵 (나중에 캐시에 저장할 수 있도록)
     */
    fun createLeatherHorseArmorJson(modelsDirectory: File): Map<String, Int> {
        val itemModelsDir = File(resourcePackPath, "assets/minecraft/items")
        itemModelsDir.mkdirs()
        
        val cases = JsonArray()
        val playerHeadCases = JsonArray() // player 모델용 cases
        val boneCustomModelDataMap = mutableMapOf<String, Int>()
        
        var customModelData = 1
        var playerHeadCustomModelData = 1

        
        // 파일이 실제로 생성될 때까지 재시도 (최대 10번, 각 100ms 대기)
        var retryCount = 0
        val maxRetries = 10
        var modelDirs: Array<File> = emptyArray()
        var foundValidFiles = false
        
        while (retryCount < maxRetries) {
            if (modelsDirectory.exists()) {
                // 디렉토리 새로고침
                val dirs = modelsDirectory.listFiles { file ->
                    file.isDirectory
                } ?: emptyArray()
                
                // 모델 폴더가 있고, 그 안에 JSON 파일이 있는지 확인
                val hasBoneFiles = dirs.any { modelDir ->
                    val boneFiles = modelDir.listFiles { file ->
                        file.isFile && file.extension.equals("json", ignoreCase = true)
                    } ?: emptyArray()
                    boneFiles.isNotEmpty() && boneFiles.all { it.exists() && it.canRead() }
                }
                
                if (hasBoneFiles && dirs.isNotEmpty()) {
                    modelDirs = dirs
                    foundValidFiles = true
                    break
                }
            }
            
            retryCount++
            if (retryCount < maxRetries) {
                Thread.sleep(100)
                ConsoleLogger.debug("${prefix} 파일 시스템 동기화 대기 중... (${retryCount}/${maxRetries})")
            }
        }
        
        if (!foundValidFiles) {
            // 재시도 후에도 파일을 찾지 못한 경우 최종 시도
            modelDirs = modelsDirectory.listFiles { file ->
                file.isDirectory
            } ?: emptyArray()
        }
        
        if (!modelsDirectory.exists()) {
            ConsoleLogger.warn("${prefix} 경고: 모델 디렉토리 없음: ${modelsDirectory.absolutePath}")
            ConsoleLogger.warn("${prefix} leather_horse_armor.json은 빈 cases 배열로 생성됨")
        } else if (!modelsDirectory.isDirectory) {
            ConsoleLogger.warn("${prefix} 경고: 모델 디렉토리가 디렉토리가 아님: ${modelsDirectory.absolutePath}")
        } else {
            
            if (modelDirs.isEmpty()) {
                ConsoleLogger.warn("${prefix} 경고: 모델 폴더 없음. 모델 폴더 생성 및 Bone JSON 파일 추가 필요")
            }
            
            // 각 모델 폴더 처리
            modelDirs.forEach { modelDir ->
                val modelName = modelDir.name
                
                // 디렉토리 리프레시를 위해 파일 목록 다시 읽기
                val boneFiles = modelDir.listFiles { file ->
                    file.isFile && file.extension.equals("json", ignoreCase = true)
                } ?: emptyArray()
                
                // 파일이 실제로 존재하는지 다시 확인
                val validBoneFiles = boneFiles.filter { it.exists() && it.canRead() }
                
                // 각 Bone 파일 처리
                validBoneFiles.forEach { boneFile ->
                    try {
                        // 파일명에서 Bone 이름 추출 (예: "boneName(uuid.json" -> "boneName")
                        val nameWithoutExt = boneFile.nameWithoutExtension
                        val boneName = nameWithoutExt.getNamePart(0)
                        
                        if (boneName != null && boneName.isNotEmpty()) {
                            // Case 항목 생성
                            val case = JsonObject()
                            val whenValue = "$modelName:$boneName"
                            case.addProperty("when", whenValue)
                            
                            // Model 객체 생성
                            val modelObj = JsonObject()
                            modelObj.addProperty("type", "model")
                            modelObj.addProperty("model", "cinematiccore:$modelName/$boneName")
                            
                            // tints 추가 (player 모델이 아니거나, player 모델이지만 특정 bone 목록에 포함되지 않은 경우)
                            val shouldUsePlayerHead = modelName.equals("player", ignoreCase = true) && isPlayerHeadBone(boneName)
                            if (!shouldUsePlayerHead) {
                                val tints = JsonArray()
                                val tint = JsonObject()
                                tint.addProperty("type", "minecraft:dye")
                                tint.addProperty("default", -1)
                                tints.add(tint)
                                modelObj.add("tints", tints)
                            }
                            
                            case.add("model", modelObj)
                            
                            // 모델 이름이 "player"이고 특정 bone 목록에 포함된 경우 player_head.json에 추가
                            // 그 외는 모두 leather_horse_armor.json에 추가
                            if (shouldUsePlayerHead) {
                                playerHeadCases.add(case)
                                val boneKey = "$modelName:$boneName"
                                boneCustomModelDataMap[boneKey] = playerHeadCustomModelData
                                playerHeadCustomModelData++
                            } else {
                                cases.add(case)
                                val boneKey = "$modelName:$boneName"
                                boneCustomModelDataMap[boneKey] = customModelData
                                customModelData++
                            }
                        } else {
                            ConsoleLogger.warn("${prefix}     - 스킵: Bone 이름 추출 불가")
                        }
                    } catch (e: Exception) {
                        ConsoleLogger.error("${prefix}     - 오류: Bone 파일 처리 실패 (${boneFile.name}): ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
        
        // leather_horse_armor.json 구조 생성 (새로운 select 형식)
        val leatherHorseArmorJson = JsonObject()
        
        // Model 객체 생성
        val modelObj = JsonObject()
        modelObj.addProperty("type", "select")
        modelObj.addProperty("property", "custom_model_data")
        
        // Fallback 생성
        val fallback = JsonObject()
        fallback.addProperty("type", "model")
        fallback.addProperty("model", "item/leather_horse_armor")
        modelObj.add("fallback", fallback)
        
        // Cases 추가
        modelObj.add("cases", cases)
        
        leatherHorseArmorJson.add("model", modelObj)
        
        // 파일 저장
        val outputFile = File(itemModelsDir, "leather_horse_armor.json")
        outputFile.writeText(gson.toJson(leatherHorseArmorJson))
        
        // player_head.json 구조 생성 (player 모델이 있는 경우)
        if (playerHeadCases.size() > 0) {
            val playerHeadJson = JsonObject()
            
            // Model 객체 생성
            val playerHeadModelObj = JsonObject()
            playerHeadModelObj.addProperty("type", "select")
            playerHeadModelObj.addProperty("property", "custom_model_data")
            
            // Fallback 생성
            val playerHeadFallback = JsonObject()
            playerHeadFallback.addProperty("type", "model")
            playerHeadFallback.addProperty("model", "item/player_head")
            playerHeadModelObj.add("fallback", playerHeadFallback)
            
            // Cases 추가
            playerHeadModelObj.add("cases", playerHeadCases)
            
            playerHeadJson.add("model", playerHeadModelObj)
            
            // 파일 저장
            val playerHeadOutputFile = File(itemModelsDir, "player_head.json")
            playerHeadOutputFile.writeText(gson.toJson(playerHeadJson))
        }
        
        return boneCustomModelDataMap
    }

    fun ensureBaseStructure() {
        createDirectoryStructure()
        val packMcmetaFile = File(resourcePackPath, "pack.mcmeta")
        if (!packMcmetaFile.exists()) {
            createPackMcmeta()
        }
        val blocksJsonFile = File(resourcePackPath, "assets/minecraft/atlases/blocks.json")
        if (!blocksJsonFile.exists()) {
            createBlocksJson()
        }
    }
    
    /**
     * 플러그인 resources/minecraft 폴더를 리소스팩 assets/minecraft로 복사
     */
    private fun copyResourcesMinecraftFolder() {
        if (plugin == null) {
            ConsoleLogger.warn("${prefix} 플러그인 인스턴스가 없어 resources/minecraft 폴더를 복사할 수 없습니다.")
            return
        }
        
        val targetDir = File(resourcePackPath, "assets/minecraft")
        targetDir.mkdirs()
        
        try {
            copyResourceDirectory("minecraft", targetDir)
            ConsoleLogger.success("${prefix} resources/minecraft 폴더를 assets/minecraft로 복사 완료!")
        } catch (e: Exception) {
            ConsoleLogger.warn("${prefix} resources/minecraft 폴더 복사 중 오류 발생: ${e.message}")
        }
    }
    
    /**
     * 플러그인 jar 내부의 리소스 디렉토리를 재귀적으로 복사
     * @param resourcePath 리소스 경로 (예: "minecraft")
     * @param targetDir 복사 대상 디렉토리
     */
    private fun copyResourceDirectory(resourcePath: String, targetDir: File) {
        val classLoader = plugin!!.javaClass.classLoader
        
        // 리소스 경로의 모든 항목을 찾기 위해 경로를 열어봄
        val resourceUrl = classLoader.getResource(resourcePath)
        if (resourceUrl == null) {
            // 리소스가 없으면 스킵
            return
        }
        
        // jar 내부 리소스인 경우 InputStream으로 읽어야 함
        if (resourceUrl.protocol == "jar") {
            val jarConnection = resourceUrl.openConnection() as JarURLConnection
            val jarFile = jarConnection.jarFile
            val entries = jarFile.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name

                if (!entry.isDirectory && entryName.startsWith(resourcePath)) {
                    val relativePath = entryName.removePrefix("$resourcePath/").takeIf { it.isNotEmpty() } ?: continue
                    val targetFile = File(targetDir, relativePath)
                    targetFile.parentFile?.mkdirs()

                    classLoader.getResourceAsStream(entryName)?.use { input ->
                        targetFile.writeBytes(input.readBytes())
                    }
                }
            }
        } else {
            // 파일 시스템 리소스인 경우 (개발 환경)
            val sourceDir = File(resourceUrl.toURI())
            if (sourceDir.exists() && sourceDir.isDirectory) {
                copyDirectory(sourceDir, targetDir)
            }
        }
    }
    
    /**
     * 디렉토리를 재귀적으로 복사 (파일 시스템용)
     */
    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return
        }
        
        targetDir.mkdirs()
        
        sourceDir.listFiles()?.forEach { sourceFile ->
            val targetFile = File(targetDir, sourceFile.name)
            
            if (sourceFile.isDirectory) {
                copyDirectory(sourceFile, targetFile)
            } else {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        }
    }
    
    /**
     * camcorder.json과 camera.png를 리소스팩에 복사
     */
    private fun copyCamcorderResources() {
        if (plugin == null) {
            ConsoleLogger.warn("${prefix} 플러그인 인스턴스가 없어 camcorder 리소스를 복사할 수 없습니다.")
            return
        }
        
        try {
            // camcorder.json 복사
            copyResourceFile("camcorder.json", File(resourcePackPath, "assets/cinematiccore/models/camcorder/camcorder.json"))
            
            // camera.png 복사
            copyResourceFile("camera.png", File(resourcePackPath, "assets/cinematiccore/textures/entity/camcorder/camera.png"))
        } catch (e: Exception) {
            ConsoleLogger.warn("${prefix} camcorder 리소스 복사 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * ender_pearl.json을 리소스팩에 복사
     */
    private fun copyEnderPearlResource() {
        if (plugin == null) {
            ConsoleLogger.warn("${prefix} 플러그인 인스턴스가 없어 ender_pearl 리소스를 복사할 수 없습니다.")
            return
        }
        
        try {
            // ender_pearl.json 복사
            copyResourceFile("ender_pearl.json", File(resourcePackPath, "assets/minecraft/items/ender_pearl.json"))
        } catch (e: Exception) {
            ConsoleLogger.warn("${prefix} ender_pearl 리소스 복사 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 플러그인 jar 내부의 단일 리소스 파일을 복사
     * @param resourceName 리소스 파일명 (예: "camcorder.json")
     * @param targetFile 복사 대상 파일
     */
    private fun copyResourceFile(resourceName: String, targetFile: File) {
        val classLoader = plugin!!.javaClass.classLoader
        
        // 리소스 URL 가져오기
        val resourceUrl = classLoader.getResource(resourceName)
        if (resourceUrl == null) {
            ConsoleLogger.warn("${prefix} 리소스를 찾을 수 없음: $resourceName")
            return
        }
        
        // 대상 파일의 부모 디렉토리 생성
        targetFile.parentFile?.mkdirs()
        
        // jar 내부 리소스인 경우
        if (resourceUrl.protocol == "jar") {
            classLoader.getResourceAsStream(resourceName)?.use { input ->
                targetFile.writeBytes(input.readBytes())
                ConsoleLogger.info("${prefix} $resourceName 복사 완료 (jar)")
            } ?: ConsoleLogger.warn("${prefix} $resourceName 스트림을 열 수 없습니다.")
        } else {
            // 파일 시스템 리소스인 경우 (개발 환경)
            val sourceFile = File(resourceUrl.toURI())
            if (sourceFile.exists() && sourceFile.isFile) {
                sourceFile.copyTo(targetFile, overwrite = true)
                ConsoleLogger.info("${prefix} $resourceName 복사 완료 (파일 시스템)")
            } else {
                ConsoleLogger.warn("${prefix} $resourceName 파일이 존재하지 않습니다: ${sourceFile.absolutePath}")
            }
        }
    }
}

