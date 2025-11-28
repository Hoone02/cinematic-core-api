package org.example.hoon.cinematicCore.util.block

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * 블럭 위치 생성 유틸리티 클래스
 * 각 감지 유형에 따른 블럭 위치 리스트 생성 담당
 */
internal object BlockLocationGenerator {
    
    /**
     * 원형 블럭 위치 리스트 생성
     */
    fun getCircularBlocks(center: Location, radius: Int): List<Location> {
        val locations = mutableListOf<Location>()
        val centerX = center.blockX
        val centerZ = center.blockZ
        val radiusSquared = radius * radius
        
        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                val dx = x - centerX
                val dz = z - centerZ
                val distanceSquared = dx * dx + dz * dz
                
                if (distanceSquared <= radiusSquared) {
                    locations.add(Location(center.world, x.toDouble(), center.y, z.toDouble()))
                }
            }
        }
        
        return locations
    }
    
    /**
     * 전방 사각형 블럭 위치 리스트 생성
     */
    fun getForwardRectangleBlocks(player: Player, width: Int, length: Int): List<Location> {
        val locations = mutableListOf<Location>()
        val playerLoc = player.location
        val direction = playerLoc.direction.normalize()
        
        val halfWidth = width / 2.0
        val up = Vector(0, 1, 0)
        val right = direction.clone().crossProduct(up).normalize()
        
        for (i in -halfWidth.toInt()..halfWidth.toInt()) {
            for (j in 0 until length) {
                val offset = right.clone().multiply(i.toDouble())
                    .add(direction.clone().multiply(j.toDouble() + 0.5))
                
                val blockLoc = playerLoc.clone().add(offset)
                locations.add(Location(blockLoc.world, blockLoc.blockX.toDouble(), playerLoc.y, blockLoc.blockZ.toDouble()))
            }
        }
        
        return locations
    }
    
    /**
     * 원형 가장자리 블럭 위치 리스트 생성
     */
    fun getCircularEdgeBlocks(center: Location, radius: Int, edgeRange: Int): List<Location> {
        val locations = mutableListOf<Location>()
        val centerX = center.blockX
        val centerZ = center.blockZ
        val radiusSquared = radius * radius
        val innerRadiusSquared = (radius - edgeRange).coerceAtLeast(0).let { it * it }
        
        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                val dx = x - centerX
                val dz = z - centerZ
                val distanceSquared = dx * dx + dz * dz
                
                if (distanceSquared <= radiusSquared && distanceSquared >= innerRadiusSquared) {
                    locations.add(Location(center.world, x.toDouble(), center.y, z.toDouble()))
                }
            }
        }
        
        return locations
    }
}

