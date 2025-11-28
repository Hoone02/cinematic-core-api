package org.example.hoon.cinematicCore.model.loader

import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.parser.BlockbenchModelParser
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.prefix
import java.io.File

/**
 * models 폴더에서 .bbmodel 파일을 로드하는 책임을 가진 클래스
 * 단일 책임 원칙: 파일 시스템 접근 및 파일 로딩만 담당
 */
class ModelFileLoader(
    private val modelsDirectory: File,
    private val parser: BlockbenchModelParser = BlockbenchModelParser()
) {
    
    /**
     * models 폴더에서 모든 .bbmodel 파일 로드
     * 
     * @return 로드된 BlockbenchModel 리스트
     */
    fun loadAllModels(): List<BlockbenchModel> {
        if (!modelsDirectory.exists() || !modelsDirectory.isDirectory) {
            ConsoleLogger.warn("모델 폴더 없음: ${modelsDirectory.absolutePath}")
            return emptyList()
        }
        
        val modelFiles = modelsDirectory.listFiles { file ->
            file.isFile && file.extension.equals("bbmodel", ignoreCase = true)
        } ?: emptyArray()
        
        if (modelFiles.isEmpty()) {
            ConsoleLogger.warn("모델 폴더에 .bbmodel 파일 없음: ${modelsDirectory.absolutePath}")
            return emptyList()
        }
        
        ConsoleLogger.reference("${prefix} ${modelFiles.size}개 .bbmodel 파일 찾음")
        
        return modelFiles.mapNotNull { file ->
            try {
                loadModel(file)
            } catch (e: Exception) {
                ConsoleLogger.error("모델 파일 로드 실패 (${file.name}): ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 특정 .bbmodel 파일 로드
     * 
     * @param modelFile 로드할 .bbmodel 파일
     * @return 파싱된 BlockbenchModel
     * @throws Exception 파일 읽기 또는 파싱 실패 시
     */
    fun loadModel(modelFile: File): BlockbenchModel {
        if (!modelFile.exists()) {
            throw IllegalArgumentException("파일이 존재하지 않습니다: ${modelFile.absolutePath}")
        }
        
        if (!modelFile.isFile) {
            throw IllegalArgumentException("파일이 아닙니다: ${modelFile.absolutePath}")
        }
        
        if (!modelFile.extension.equals("bbmodel", ignoreCase = true)) {
            throw IllegalArgumentException("bbmodel 파일이 아닙니다: ${modelFile.absolutePath}")
        }
        
        ConsoleLogger.reference("${prefix} 모델 파일 로딩: ${modelFile.name}")
        val jsonContent = modelFile.readText()
        
        val model = parser.parse(jsonContent)
        
        // 파일 이름에 "freecam-" 태그가 있는지 확인
        val isFreecam = modelFile.nameWithoutExtension.startsWith("freecam-", ignoreCase = true)
        
        return if (isFreecam) {
            model.copy(isFreecam = true)
        } else {
            model
        }
    }
    
    /**
     * 특정 이름의 모델 파일 로드
     * 
     * @param modelName 모델 파일명 (확장자 포함 또는 제외 모두 가능)
     * @return 파싱된 BlockbenchModel, 파일을 찾지 못한 경우 null
     */
    fun loadModelByName(modelName: String): BlockbenchModel? {
        val fileName = if (modelName.endsWith(".bbmodel", ignoreCase = true)) {
            modelName
        } else {
            "$modelName.bbmodel"
        }
        
        val modelFile = File(modelsDirectory, fileName)
        
        return if (modelFile.exists()) {
            try {
                loadModel(modelFile)
            } catch (e: Exception) {
                ConsoleLogger.error("모델 파일 로드 실패 (${modelFile.name}): ${e.message}")
                null
            }
        } else {
            ConsoleLogger.warn("모델 파일 없음: ${modelFile.absolutePath}")
            null
        }
    }
}

