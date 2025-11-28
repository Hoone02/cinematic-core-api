package org.example.hoon.cinematicCore.model.domain

import org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation

data class BlockbenchModel(
    val modelName: String,
    val elements: List<ModelElement>,
    val bones: List<Bone>,
    val textures: List<Texture> = emptyList(),
    val animations: List<BlockbenchAnimation> = emptyList(),
    val isFreecam: Boolean = false
)

