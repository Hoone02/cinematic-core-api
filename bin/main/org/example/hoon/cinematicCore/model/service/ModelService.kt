package org.example.hoon.cinematicCore.model.service

import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.model.fs.ModelFilesystem
import org.example.hoon.cinematicCore.model.loader.ModelFileLoader
import org.example.hoon.cinematicCore.model.processor.ModelProcessor
import org.example.hoon.cinematicCore.model.store.ScaledBoneCache
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.prefix

/**
 * 모델 처리 서비스 클래스
 * 모델 로딩, 파싱, 처리 전반을 관리하는 비즈니스 로직 담당
 */
class ModelService(
    plugin: Plugin,
    private val filesystem: ModelFilesystem = ModelFilesystem(plugin)
) {
    val scaledBoneCache = ScaledBoneCache()
    private lateinit var modelFileLoader: ModelFileLoader
    private lateinit var modelProcessor: ModelProcessor
    
    /**
     * 서비스 초기화
     */
    fun initialize() {
        // 모델 로더 초기화
        val modelsDir = filesystem.ensureModelsDirectory()
        modelFileLoader = ModelFileLoader(modelsDir)
        
        // 모델 프로세서 초기화
        modelProcessor = ModelProcessor(filesystem, scaledBoneCache)
    }
    
    /**
     * 모든 모델 처리
     * models 폴더에서 .bbmodel 파일을 로드하고 처리
     * 
     * @return 생성된 Bone JSON 파일의 총 개수
     */
    fun processAllModels(): Int {
        modelProcessor.clearCaches()
        scaledBoneCache.clear()

        val models = modelFileLoader.loadAllModels()
        
        if (models.isEmpty()) {
            ConsoleLogger.warn("${prefix} 처리할 모델 없음. models 폴더에 .bbmodel 파일 추가 필요")
            return 0
        }
        
        ConsoleLogger.reference("${prefix} ${models.size}개 모델 처리 중")
        
        var totalBoneFiles = 0
        
        // 각 모델에 대해 처리
        models.forEach { model ->
            try {
                modelProcessor.process(model)
                // 모델 처리 완료 후 해당 모델의 Bone 파일 개수 확인
                val modelDir = filesystem.resolveResourceModelDirectory(model.modelName)
                if (modelDir.exists()) {
                    val boneFiles = modelDir.listFiles { file ->
                        file.isFile && file.extension.equals("json", ignoreCase = true)
                    } ?: emptyArray()
                    totalBoneFiles += boneFiles.size
                }
            } catch (e: Exception) {
                ConsoleLogger.error("${prefix} 모델 처리 오류 (${model.modelName}): ${e.message}")
                e.printStackTrace()
            }
        }
        
        ConsoleLogger.success("${prefix} 모델 처리 완료. 총 ${totalBoneFiles}개 Bone JSON 파일 생성됨")
        return totalBoneFiles
    }
    
    /**
     * 모델 파일 로더 반환
     */
    fun getModelFileLoader(): ModelFileLoader {
        if (!::modelFileLoader.isInitialized) {
            initialize()
        }
        return modelFileLoader
    }
    
    /**
     * 삭제된 모델 정리
     * plugins/CinematicCore/models에 없는 모델의 폴더와 파일을 삭제
     * 
     * @return 삭제된 모델 개수
     */
    fun deleteRemovedModels(): Int {
        // models 폴더에 있는 .bbmodel 파일 목록 가져오기 (확장자 제외)
        val existingModelNames = filesystem.existingModelNames()
        
        // 삭제 대상에서 제외할 모델 목록 (리소스에서 직접 복사하는 모델)
        val protectedModels = setOf("camcorder")
        
        ConsoleLogger.reference("${prefix} 현재 models 폴더의 모델: ${existingModelNames.joinToString()}")
        
        var deletedCount = 0
        
        // CC_Resource/assets/cinematiccore/models 폴더의 모든 폴더 확인
        filesystem.listResourceModelDirectories().forEach { modelDir ->
            val modelName = modelDir.name
            
            // 보호된 모델이거나 models 폴더에 해당 모델이 없으면 삭제
            if (protectedModels.contains(modelName.lowercase())) {
                ConsoleLogger.reference("${prefix} 보호된 모델 (삭제 제외): $modelName")
                return@forEach
            }
            
            if (!existingModelNames.contains(modelName)) {
                ConsoleLogger.reference("${prefix} 삭제할 모델 발견: $modelName")
                
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                        ConsoleLogger.warn("${prefix}   - 모델 폴더 삭제: ${modelDir.absolutePath}")
                    }
                    
                    val textureDir = filesystem.resolveResourceTextureDirectory(modelName)
                    if (textureDir.exists()) {
                        textureDir.deleteRecursively()
                        ConsoleLogger.warn("${prefix}   - 텍스처 폴더 삭제: ${textureDir.absolutePath}")
                    }
                    
                    deletedCount++
                } catch (e: Exception) {
                    ConsoleLogger.error("${prefix}   - 삭제 실패 (${modelName}): ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        if (deletedCount > 0) {
            ConsoleLogger.success("${prefix} 삭제 완료: 총 ${deletedCount}개 모델 삭제됨")
        } else {
            ConsoleLogger.reference("${prefix} 삭제할 모델 없음")
        }
        
        return deletedCount
    }
}

