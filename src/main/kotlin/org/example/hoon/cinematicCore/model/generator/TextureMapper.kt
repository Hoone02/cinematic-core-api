package org.example.hoon.cinematicCore.model.generator

import com.google.gson.JsonObject
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.ModelElement

class TextureMapper {

    fun collectTextureIds(elements: List<ModelElement>, model: BlockbenchModel): Set<String> {
        val defaultTextureId = model.textures.firstOrNull()?.id
        
        return elements.flatMap { element ->
            listOfNotNull(
                element.faces.north?.textureId ?: defaultTextureId,
                element.faces.east?.textureId ?: defaultTextureId,
                element.faces.south?.textureId ?: defaultTextureId,
                element.faces.west?.textureId ?: defaultTextureId,
                element.faces.up?.textureId ?: defaultTextureId,
                element.faces.down?.textureId ?: defaultTextureId
            )
        }.distinct().toSet()
    }

    fun buildTextureObject(
        model: BlockbenchModel,
        textureIds: Set<String>,
        textureKey: String,
        texturePath: String
    ): JsonObject {
        val texturesJson = JsonObject()

        // 실제로 존재하는 textureId만 등록 (존재하지 않는 ID는 제외)
        textureIds.forEach { textureId ->
            val texture = model.textures.find { it.id == textureId || it.uuid == textureId }
            if (texture != null) {
                val texturePathValue = "${texturePath}${model.modelName}/${texture.name}"
                texturesJson.addProperty(textureId, texturePathValue)
            }
            // 텍스처를 찾을 수 없는 경우는 등록하지 않음 (존재하지 않는 ID 제외)
        }

        // textureIds가 비어있거나 모든 ID가 존재하지 않는 경우 기본 텍스처 사용
        if (texturesJson.size() == 0) {
            val defaultTexture = model.textures.firstOrNull()
            if (defaultTexture != null) {
                val texturePathValue = "${texturePath}${model.modelName}/${defaultTexture.name}"
                texturesJson.addProperty(defaultTexture.id, texturePathValue)
            } else {
                texturesJson.addProperty(textureKey, "${texturePath}${model.modelName}/texture")
            }
        }

        return texturesJson
    }
}

