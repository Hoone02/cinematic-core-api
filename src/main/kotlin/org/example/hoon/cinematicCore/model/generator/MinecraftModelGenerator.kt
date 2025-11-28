package org.example.hoon.cinematicCore.model.generator

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.getChildElements
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.prefix

class MinecraftModelGenerator(
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    private val textureMapper: TextureMapper = TextureMapper(),
    private val elementBuilder: ElementJsonBuilder = ElementJsonBuilder(),
    private val scaleCalculator: ScaleCalculator = ScaleCalculator()
) {

    fun generateMinecraftModel(
        model: BlockbenchModel,
        bone: Bone,
        textureKey: String = "0",
        texturePath: String = "cinematiccore:entity/"
    ): Pair<String, Double> {
        val rootObject = JsonObject()
        val childElements = model.getChildElements(bone)
        val scaleContext = scaleCalculator.evaluate(bone, childElements)

        if (scaleContext.isOversized) {
            ConsoleLogger.warn("${prefix} ${bone.name} 사이즈가 큼")
        }

        val textureIds = textureMapper.collectTextureIds(childElements, model)
        val texturesJson = textureMapper.buildTextureObject(model, textureIds, textureKey, texturePath)
        rootObject.add("textures", texturesJson)

        val elementsArray = JsonArray()
        childElements.forEach { element ->
            elementsArray.add(elementBuilder.buildElement(element, model, bone, scaleContext))
        }
        rootObject.add("elements", elementsArray)
        rootObject.add("display", defaultDisplaySection())

        val finalScale = if (scaleContext.isOversized) scaleContext.scale else 1.0
        return gson.toJson(rootObject) to finalScale
    }

    fun generateMinecraftModelMultipleBones(
        model: BlockbenchModel,
        bones: List<Bone>,
        textureKey: String = "0",
        texturePath: String = "cinematiccore:entity/"
    ): String {
        val rootObject = JsonObject()
        val allElements = bones.flatMap { model.getChildElements(it) }

        val textureIds = textureMapper.collectTextureIds(allElements, model)
        val texturesJson = textureMapper.buildTextureObject(model, textureIds, textureKey, texturePath)
        rootObject.add("textures", texturesJson)

        val elementsArray = JsonArray()
        bones.forEach { bone ->
            val childElements = model.getChildElements(bone)
            val scaleContext = scaleCalculator.evaluate(bone, childElements, oversizedScale = 0.5)
            if (scaleContext.isOversized) {
                ConsoleLogger.warn("${prefix} ${bone.name} 사이즈가 큼 (다중 Bone)")
            }
            childElements.forEach { element ->
                elementsArray.add(elementBuilder.buildElement(element, model, bone, scaleContext))
            }
        }
        rootObject.add("elements", elementsArray)
        rootObject.add("display", defaultDisplaySection())

        return gson.toJson(rootObject)
    }

    private fun defaultDisplaySection(): JsonObject {
        val display = JsonObject()
        val gui = JsonObject()
        val rotation = JsonArray()
        rotation.add(30)
        rotation.add(225)
        rotation.add(0)
        gui.add("rotation", rotation)
        display.add("gui", gui)
        return display
    }
}

