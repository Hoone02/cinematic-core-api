package org.example.hoon.cinematicCore.util.block

import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay

/**
 * 블럭 복원 정보
 */
internal data class BlockRestoreInfo(
    val block: Block,
    val originalBlockData: BlockData,
    val blockDisplay: BlockDisplay,
    val initialYaw: Float,
    val initialPitch: Float,
    val initialRoll: Float
)

