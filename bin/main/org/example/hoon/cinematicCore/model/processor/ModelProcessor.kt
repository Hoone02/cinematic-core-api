package org.example.hoon.cinematicCore.model.processor

import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.getChildElements
import org.example.hoon.cinematicCore.model.fs.ModelFilesystem
import org.example.hoon.cinematicCore.model.generator.MinecraftModelGenerator
import org.example.hoon.cinematicCore.model.store.ModelRotationRegistry
import org.example.hoon.cinematicCore.model.store.ScaledBoneCache
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.TextureSaver
import org.example.hoon.cinematicCore.util.prefix
import java.io.File

/**
 * 단일 모델을 처리하는 책임을 가진 클래스
 * 텍스처 저장 및 Bone JSON 생성 담당
 */
class ModelProcessor(
    private val filesystem: ModelFilesystem,
    private val scaledBoneCache: ScaledBoneCache,
    private val generator: MinecraftModelGenerator = MinecraftModelGenerator()
) {

    /**
     * 모델 처리 (텍스처 저장 및 JSON 생성)
     *
     * @param model 처리할 BlockbenchModel
     */
    fun process(model: BlockbenchModel) {
        ConsoleLogger.success("${prefix} 모델 처리 시작: ${model.modelName}")

        // 텍스처 저장
        saveTextures(model)

        // Bone JSON 생성
        generateBoneModels(model)

        ConsoleLogger.success("${prefix} 모델 처리 완료: ${model.modelName}\n")
    }

    /**
     * 생성된 캐시와 회전 레지스트리를 초기화한다.
     */
    fun clearCaches() {
        scaledBoneCache.clear()
        ModelRotationRegistry.clearAll()
    }

    /**
     * 모델의 텍스처 저장
     */
    private fun saveTextures(model: BlockbenchModel) {
        if (model.textures.isEmpty()) {
            return
        }

        // 경로: plugins/CinematicCore/CC_Resource/assets/cinematiccore/textures/entity/모델명폴더
        val texturesDir = filesystem.ensureTextureDirectory(model.modelName)

        // 모든 텍스처 저장
        TextureSaver.saveAllTextures(model.textures, texturesDir)
    }

    /**
     * 모델의 모든 Bone에 대해 JSON 모델 생성
     */
    private fun generateBoneModels(model: BlockbenchModel) {
        val modelsDir = filesystem.ensureModelJsonDirectory(model.modelName)

        model.bones.forEach { bone ->
            // 자식 큐브가 없는 bone은 건너뛰기
            val childElements = model.getChildElements(bone)
            if (childElements.isEmpty()) {
                return@forEach
            }

            // 원본 rotation 데이터를 레지스트리에 저장
            ModelRotationRegistry.putBoneRotation(model.modelName, bone, childElements)

            // Minecraft JSON 모델 생성
            val (minecraftJson, scale) = generator.generateMinecraftModel(model, bone)

            // 크기가 줄어든 경우 메모리 캐시에 저장
            if (scale < 1.0) {
                scaledBoneCache.put(model.modelName, bone, minecraftJson, scale)
                ConsoleLogger.info("${prefix} 크기 조절된 bone 메모리 저장: ${model.modelName}/${bone.name} (스케일: ${String.format("%.2f", scale)})")
            }

            // 파일 저장 (명시적으로 flush하여 디스크에 쓰기 보장)
            val outputFile = File(modelsDir, "${bone.name}.json")
            outputFile.outputStream().use { stream ->
                stream.write(minecraftJson.toByteArray(Charsets.UTF_8))
                stream.flush()
                // Windows에서는 fsync가 없으므로 flush만 사용
            }

            // 파일이 디스크에 완전히 쓰여졌는지 확인 (재시도 로직)
            var retries = 0
            while (retries < 5) {
                if (outputFile.exists() && outputFile.canRead() && outputFile.length() > 0) {
                    break
                }
                Thread.sleep(50)
                retries++
            }

            if (!outputFile.exists() || !outputFile.canRead() || outputFile.length() == 0L) {
                throw IllegalStateException("파일이 제대로 생성되지 않음: ${outputFile.absolutePath}")
            }
        }
    }
}

