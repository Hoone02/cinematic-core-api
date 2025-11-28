package org.example.hoon.cinematicCore.model.loader

import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.getNamePart
import java.io.File

/**
 * 모델 폴더에서 Bone JSON 파일을 로드하는 책임을 가진 클래스
 * 단일 책임 원칙: 파일 시스템 접근 및 파일 로딩만 담당
 */
class BoneFileLoader(
    private val baseModelsDirectory: File
) {
    
    /**
     * 특정 모델 이름으로 해당 모델의 모든 Bone 파일 로드
     * 
     * @param modelName 모델 이름
     * @return 로드된 Bone 파일 내용 맵 (파일명 -> JSON 내용)
     */
    fun loadAllBonesByModelName(modelName: String): Map<String, String> {
        val modelDir = File(baseModelsDirectory, modelName)
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            ConsoleLogger.warn("모델 폴더 없음: ${modelDir.absolutePath}")
            return emptyMap()
        }
        
        val boneFiles = modelDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: emptyArray()
        
        if (boneFiles.isEmpty()) {
            ConsoleLogger.warn("모델 폴더에 Bone 파일 없음: ${modelDir.absolutePath}")
            return emptyMap()
        }
        
        ConsoleLogger.info("${boneFiles.size}개 Bone 파일 찾음 (모델: $modelName)")
        
        return boneFiles.associate { file ->
            try {
                val content = file.readText()
                file.name to content
            } catch (e: Exception) {
                ConsoleLogger.error("Bone 파일 읽기 실패 (${file.name}): ${e.message}")
                file.name to ""
            }
        }.filter { it.value.isNotEmpty() }
    }
    
    /**
     * 특정 모델의 Bone 파일 로드
     * 
     * @param modelName 모델 이름
     * @param boneFile 로드할 Bone 파일
     * @return 파일 내용 (JSON 문자열)
     * @throws Exception 파일 읽기 실패 시
     */
    fun loadBone(modelName: String, boneFile: File): String {
        val modelDir = File(baseModelsDirectory, modelName)
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            throw IllegalArgumentException("모델 폴더가 존재하지 않습니다: ${modelDir.absolutePath}")
        }
        
        if (!boneFile.exists()) {
            throw IllegalArgumentException("파일이 존재하지 않습니다: ${boneFile.absolutePath}")
        }
        
        if (!boneFile.isFile) {
            throw IllegalArgumentException("파일이 아닙니다: ${boneFile.absolutePath}")
        }
        
        if (!boneFile.extension.equals("json", ignoreCase = true)) {
            throw IllegalArgumentException("JSON 파일이 아닙니다: ${boneFile.absolutePath}")
        }
        
        ConsoleLogger.info("Bone 파일 로딩: ${boneFile.name} (모델: $modelName)")
        return boneFile.readText()
    }
    
    /**
     * 모델 이름과 Bone 이름으로 Bone 파일을 찾아서 로드
     * 
     * @param modelName 모델 이름
     * @param boneName Bone 이름 (확장자 제외)
     * @return 파일 내용 (JSON 문자열), 파일을 찾지 못한 경우 null
     */
    fun loadBoneByName(modelName: String, boneName: String): String? {
        val modelDir = File(baseModelsDirectory, modelName)
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            ConsoleLogger.warn("모델 폴더 없음: ${modelDir.absolutePath}")
            return null
        }
        
        // 파일명 형식: boneName(uuid.json
        // boneName으로 시작하는 파일을 찾음
        val boneFiles = modelDir.listFiles { file ->
            file.isFile && 
            file.extension.equals("json", ignoreCase = true) &&
            file.nameWithoutExtension.startsWith("$boneName(")
        } ?: emptyArray()
        
        if (boneFiles.isEmpty()) {
            ConsoleLogger.warn("Bone 파일 없음 (모델: $modelName, Bone: $boneName)")
            return null
        }
        
        // 여러 개가 있으면 첫 번째 것을 사용
        val boneFile = boneFiles.first()
        if (boneFiles.size > 1) {
            ConsoleLogger.warn("경고: Bone 이름 '$boneName'에 해당하는 파일이 여러 개 있음. 첫 번째 파일 사용: ${boneFile.name}")
        }
        
        return try {
            loadBone(modelName, boneFile)
        } catch (e: Exception) {
            ConsoleLogger.error("Bone 파일 로드 실패 (${boneFile.name}): ${e.message}")
            null
        }
    }
    
    /**
     * 모델 이름과 Bone UUID로 Bone 파일을 찾아서 로드
     * 
     * @param modelName 모델 이름
     * @param boneUuid Bone UUID
     * @return 파일 내용 (JSON 문자열), 파일을 찾지 못한 경우 null
     */
    fun loadBoneByUuid(modelName: String, boneUuid: String): String? {
        val modelDir = File(baseModelsDirectory, modelName)
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            ConsoleLogger.warn("모델 폴더 없음: ${modelDir.absolutePath}")
            return null
        }
        
        // 파일명 형식: boneName(uuid.json
        // UUID로 파일을 찾음
        val boneFiles = modelDir.listFiles { file ->
            file.isFile && 
            file.extension.equals("json", ignoreCase = true) &&
            file.nameWithoutExtension.contains("($boneUuid")
        } ?: emptyArray()
        
        if (boneFiles.isEmpty()) {
            ConsoleLogger.warn("Bone 파일 없음 (모델: $modelName, UUID: $boneUuid)")
            return null
        }
        
        val boneFile = boneFiles.first()
        if (boneFiles.size > 1) {
            ConsoleLogger.warn("경고: Bone UUID '$boneUuid'에 해당하는 파일이 여러 개 있음. 첫 번째 파일 사용: ${boneFile.name}")
        }
        
        return try {
            loadBone(modelName, boneFile)
        } catch (e: Exception) {
            ConsoleLogger.error("Bone 파일 로드 실패 (${boneFile.name}): ${e.message}")
            null
        }
    }
    
    /**
     * 모델 이름으로 해당 모델의 모든 Bone 파일 이름만 반환
     * 
     * @param modelName 모델 이름
     * @return Bone 파일 이름 리스트
     */
    fun name(modelName: String): List<String> {
        val modelDir = File(baseModelsDirectory, modelName)
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            ConsoleLogger.warn("모델 폴더 없음: ${modelDir.absolutePath}")
            return emptyList()
        }
        
        val boneFiles = modelDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: emptyArray()
        
        return boneFiles.map { it.name }
    }
    
    /**
     * 모델 이름으로 해당 모델의 모든 Bone 파일 정보 가져오기
     * 
     * @param modelName 모델 이름
     * @return Bone 파일 정보 리스트 (파일명, Bone 이름, UUID)
     */
    fun getBoneFilesInfo(modelName: String): List<BoneFileInfo> {
        val modelDir = File(baseModelsDirectory, modelName)
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            ConsoleLogger.warn("모델 폴더 없음: ${modelDir.absolutePath}")
            return emptyList()
        }
        
        val boneFiles = modelDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: emptyArray()
        
        return boneFiles.mapNotNull { file ->
            try {
                val nameWithoutExt = file.nameWithoutExtension
                val boneName = nameWithoutExt.getNamePart(0) ?: ""
                val uuidPart = nameWithoutExt.getNamePart(1) ?: ""
                
                BoneFileInfo(
                    fileName = file.name,
                    boneName = boneName,
                    uuid = uuidPart,
                    file = file
                )
            } catch (e: Exception) {
                ConsoleLogger.error("Bone 파일 정보 파싱 실패 (${file.name}): ${e.message}")
                null
            }
        }
    }
    
    /**
     * Bone 파일 정보를 담는 데이터 클래스
     */
    data class BoneFileInfo(
        val fileName: String,
        val boneName: String,
        val uuid: String,
        val file: File
    )
}

