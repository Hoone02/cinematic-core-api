package org.example.hoon.cinematicCore.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.example.hoon.cinematicCore.model.domain.Texture
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max

object TextureSaver {
    /**
     * Base64 인코딩된 이미지를 PNG 파일로 저장
     * 저장 경로: texturesDir/텍스쳐이름.png
     * 
     * @param texture 텍스처 정보
     * @param texturesDir 저장할 디렉토리 (이미 모델명 폴더까지 포함된 경로)
     * @return 저장된 파일, 실패 시 null
     */
    fun saveBase64Texture(texture: Texture, texturesDir: File): File? {
        return try {
            val source = texture.source
            
            // data:image/png;base64, 형태인지 확인
            if (!source.startsWith("data:image")) {
                ConsoleLogger.warn("텍스처 ${texture.name}은 base64 형식 아님: $source")
                return null
            }
            
            // base64 데이터 추출
            val base64Data = source.substringAfter("base64,")
            val imageBytes = Base64.getDecoder().decode(base64Data)
            
            // 디렉토리 생성 (이미 모델명 폴더까지 포함)
            texturesDir.mkdirs()
            
            // 파일 확장자 추출 (기본값: png)
            val extension = if (source.contains("image/")) {
                source.substringAfter("image/").substringBefore(";").takeIf { it.isNotEmpty() } ?: "png"
            } else {
                "png"
            }
            
            // 파일명: 텍스쳐이름.확장자
            val fileName = "${texture.name}.$extension"
            val outputFile = File(texturesDir, fileName)
            
            // 바이트 배열을 이미지 파일로 저장
            val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(imageBytes))
            ImageIO.write(image, extension, outputFile)

            if (shouldCreateAnimationMetadata(texture)) {
                createAnimationMetadataFile(texture, outputFile)
            }
            
            ConsoleLogger.reference("${prefix} 텍스처 저장 완료: ${texture.name}")
            outputFile
        } catch (e: Exception) {
            ConsoleLogger.error("${prefix} 텍스처 저장 실패 (${texture.name}): ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Blockbench 모델의 모든 텍스처 저장
     * 
     * @param textures 텍스처 리스트
     * @param texturesDir 저장할 디렉토리 (모델명 폴더까지 포함된 경로)
     * @return 저장된 파일 리스트
     */
    fun saveAllTextures(textures: List<Texture>, texturesDir: File): List<File> {
        return textures.mapNotNull { texture ->
            saveBase64Texture(texture, texturesDir)
        }
    }

    private fun shouldCreateAnimationMetadata(texture: Texture): Boolean {
        val hasCustomOrder = texture.frameOrder.isNotBlank()
        val hasNonDefaultTime = texture.frameTime > 1
        val hasInterpolation = texture.frameInterpolate
        val hasCustomType = !texture.frameOrderType.equals("loop", ignoreCase = true)
        val isNonSquareTexture = texture.width != texture.height ||
            (texture.uvWidth > 0 && texture.uvHeight > 0 && texture.uvWidth != texture.uvHeight)

        return hasCustomOrder || hasNonDefaultTime || hasInterpolation || hasCustomType || isNonSquareTexture
    }

    private fun createAnimationMetadataFile(texture: Texture, textureFile: File) {
        val animationObject = JsonObject().apply {
            addProperty("frametime", max(texture.frameTime, 1))

            if (texture.frameInterpolate) {
                addProperty("interpolate", true)
            }

            if (texture.uvWidth > 0 && texture.uvWidth != texture.width) {
                addProperty("width", texture.uvWidth)
            }

            if (texture.uvHeight > 0 && texture.uvHeight != texture.height) {
                addProperty("height", texture.uvHeight)
            }

            parseFrameOrder(texture.frameOrder)?.let { frames ->
                add("frames", frames)
            }
        }

        val metadataRoot = JsonObject().apply {
            add("animation", animationObject)
        }

        val mcmetaFile = File(textureFile.parentFile, "${textureFile.name}.mcmeta")
        runCatching {
            mcmetaFile.writeText(metadataRoot.toString())
            ConsoleLogger.reference("${prefix} mcmeta 생성 완료: ${mcmetaFile.name}")
        }.onFailure { error ->
            ConsoleLogger.error("${prefix} mcmeta 생성 실패 (${texture.name}): ${error.message}")
            error.printStackTrace()
        }
    }

    private fun parseFrameOrder(frameOrder: String): JsonArray? {
        if (frameOrder.isBlank()) {
            return null
        }

        parseJsonArraySafely(frameOrder)?.let { return it }

        val numbers = frameOrder
            .split(',', ';', ' ', '\n', '\r', '\t')
            .mapNotNull { token -> token.trim().toIntOrNull() }

        if (numbers.isEmpty()) {
            return null
        }

        return JsonArray().apply {
            numbers.forEach { add(it) }
        }
    }

    private fun parseJsonArraySafely(raw: String): JsonArray? {
        return try {
            val jsonElement = JsonParser.parseString(raw)
            when {
                jsonElement.isJsonArray -> jsonElement.asJsonArray
                jsonElement.isJsonObject -> {
                    val jsonObject = jsonElement.asJsonObject
                    val framesElement = jsonObject.get("frames")
                    if (framesElement != null && framesElement.isJsonArray) {
                        framesElement.asJsonArray
                    } else {
                        null
                    }
                }
                else -> null
            }
        } catch (ex: Exception) {
            null
        }
    }
}

