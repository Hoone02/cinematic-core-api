package org.example.hoon.cinematicCore.model.fs

import org.bukkit.plugin.Plugin
import java.io.File

/**
 * 모델 관련 파일 시스템 경로와 디렉터리 관리를 담당하는 클래스.
 * 플러그인 전역에서 동일한 규칙을 공유하도록 중앙에서 관리한다.
 */
class ModelFilesystem(private val plugin: Plugin) {

    private val dataFolder: File
        get() = plugin.dataFolder

    /**
     * 플러그인의 models 디렉터리를 반환하고, 없으면 생성한다.
     */
    fun ensureModelsDirectory(): File = ensureDirectory(File(dataFolder, "models"))

    /**
     * 존재하는 .bbmodel 파일 이름(확장자 제외)의 집합을 반환한다.
     */
    fun existingModelNames(): Set<String> {
        val modelsDir = ensureModelsDirectory()
        val modelFiles = modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("bbmodel", ignoreCase = true)
        } ?: emptyArray()

        return modelFiles.map { it.nameWithoutExtension }.toSet()
    }

    /**
     * 모델 JSON 파일이 저장되는 디렉터리를 반환하고, 없으면 생성한다.
     */
    fun ensureModelJsonDirectory(modelName: String): File =
        ensureDirectory(File(resourceModelsRoot(), modelName))

    /**
     * 텍스처 파일이 저장되는 디렉터리를 반환하고, 없으면 생성한다.
     */
    fun ensureTextureDirectory(modelName: String): File =
        ensureDirectory(File(resourceTexturesRoot(), modelName))

    /**
     * 리소스팩 내부의 모델 디렉터리 목록을 반환한다.
     */
    fun listResourceModelDirectories(): List<File> {
        val root = resourceModelsRoot()
        return root.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
    }

    /**
     * 리소스팩 내 특정 모델 디렉터리를 반환한다. (존재하지 않아도 생성하지는 않는다)
     */
    fun resolveResourceModelDirectory(modelName: String): File =
        File(resourceModelsRoot(), modelName)

    /**
     * 리소스팩 내 특정 텍스처 디렉터리를 반환한다. (존재하지 않아도 생성하지는 않는다)
     */
    fun resolveResourceTextureDirectory(modelName: String): File =
        File(resourceTexturesRoot(), modelName)

    /**
     * 리소스팩에 저장된 모델/텍스처 자원을 제거한다.
     */
    fun deleteModelResources(modelName: String) {
        val modelDir = resolveResourceModelDirectory(modelName)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }

        val textureDir = resolveResourceTextureDirectory(modelName)
        if (textureDir.exists()) {
            textureDir.deleteRecursively()
        }
    }

    private fun resourceRoot(): File = ensureDirectory(File(dataFolder, "CC_Resource/assets/cinematiccore"))

    private fun resourceModelsRoot(): File = ensureDirectory(File(resourceRoot(), "models"))

    private fun resourceTexturesRoot(): File = ensureDirectory(File(resourceRoot(), "textures/entity"))

    private fun ensureDirectory(directory: File): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }
}

