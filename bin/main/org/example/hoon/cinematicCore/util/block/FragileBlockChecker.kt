package org.example.hoon.cinematicCore.util.block

import org.bukkit.Material

/**
 * 부서지기 쉬운 블럭 체크 유틸리티
 */
internal object FragileBlockChecker {
    /**
     * 부서지기 쉬운 블럭인지 확인
     */
    fun isFragileBlock(material: Material): Boolean {
        return when {
            // 풀 및 식물류 (GRASS_BLOCK은 제외)
            (material.name.contains("GRASS", ignoreCase = true) && material != Material.GRASS_BLOCK) -> true
            material.name.contains("FERN", ignoreCase = true) -> true
            material.name.contains("FLOWER", ignoreCase = true) -> true
            material.name.contains("SAPLING", ignoreCase = true) -> true
            material.name.contains("MUSHROOM", ignoreCase = true) -> true
            material == Material.DANDELION -> true
            material == Material.POPPY -> true
            material == Material.BLUE_ORCHID -> true
            material == Material.ALLIUM -> true
            material == Material.AZURE_BLUET -> true
            material == Material.RED_TULIP -> true
            material == Material.ORANGE_TULIP -> true
            material == Material.WHITE_TULIP -> true
            material == Material.PINK_TULIP -> true
            material == Material.OXEYE_DAISY -> true
            material == Material.CORNFLOWER -> true
            material == Material.LILY_OF_THE_VALLEY -> true
            material == Material.SUNFLOWER -> true
            material == Material.LILAC -> true
            material == Material.ROSE_BUSH -> true
            material == Material.PEONY -> true
            
            // 문
            material.name.contains("DOOR", ignoreCase = true) -> true
            material.name.contains("TRAPDOOR", ignoreCase = true) -> true
            
            // 작물
            material.name.contains("CROP", ignoreCase = true) -> true
            material == Material.WHEAT -> true
            material == Material.CARROTS -> true
            material == Material.POTATOES -> true
            material == Material.BEETROOTS -> true
            material == Material.MELON_STEM -> true
            material == Material.PUMPKIN_STEM -> true
            material == Material.TORCHFLOWER_CROP -> true
            material == Material.PITCHER_CROP -> true
            
            // 기타 부서지기 쉬운 블럭
            material == Material.DEAD_BUSH -> true
            material == Material.SUGAR_CANE -> true
            material == Material.BAMBOO -> true
            material == Material.CACTUS -> true
            material == Material.VINE -> true
            material == Material.LILY_PAD -> true
            material == Material.SEAGRASS -> true
            material == Material.TALL_SEAGRASS -> true
            material == Material.KELP -> true
            material == Material.KELP_PLANT -> true
            material == Material.SEA_PICKLE -> true
            material.name.contains("CORAL", ignoreCase = true) -> true
            material.name.contains("CORAL_FAN", ignoreCase = true) -> true
            material.name.contains("CORAL_WALL_FAN", ignoreCase = true) -> true

            // 기타 제외 대상
            material == Material.BARRIER -> true
            material == Material.LIGHT -> true
            
            else -> false
        }
    }
}

