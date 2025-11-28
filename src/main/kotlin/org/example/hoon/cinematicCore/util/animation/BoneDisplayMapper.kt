package org.example.hoon.cinematicCore.util.animation

import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.example.hoon.cinematicCore.model.domain.Bone
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bone UUID와 Display 엔티티를 매핑하는 클래스
 */
class BoneDisplayMapper {
    private val boneToDisplay: MutableMap<String, ItemDisplay> = mutableMapOf()
    private val displayToBone: MutableMap<ItemDisplay, String> = mutableMapOf()
    
    /**
     * Bone과 Display를 매핑
     */
    fun map(boneUuid: String, display: ItemDisplay) {
        boneToDisplay[boneUuid] = display
        displayToBone[display] = boneUuid
    }
    
    /**
     * Bone UUID로 Display를 가져옴
     */
    fun getDisplay(boneUuid: String): ItemDisplay? {
        return boneToDisplay[boneUuid]
    }
    
    /**
     * Display로 Bone UUID를 가져옴
     */
    fun getBoneUuid(display: ItemDisplay): String? {
        return displayToBone[display]
    }
    
    /**
     * Bone으로 Display를 가져옴
     */
    fun getDisplay(bone: Bone): ItemDisplay? {
        return boneToDisplay[bone.uuid]
    }
    
    /**
     * 모든 매핑된 Bone UUID 목록을 반환
     */
    fun getAllBoneUuids(): Set<String> {
        return boneToDisplay.keys.toSet()
    }
    
    /**
     * 모든 매핑된 Display 목록을 반환
     */
    fun getAllDisplays(): Collection<ItemDisplay> {
        return boneToDisplay.values
    }
    
    /**
     * 매핑을 제거
     */
    fun remove(boneUuid: String) {
        val display = boneToDisplay.remove(boneUuid)
        display?.let { displayToBone.remove(it) }
    }
    
    /**
     * 모든 매핑을 제거
     */
    fun clear() {
        boneToDisplay.clear()
        displayToBone.clear()
    }
    
    /**
     * Bone의 최종 pivot 위치를 가져옴
     * Display의 위치와 transformation의 translation을 합산하여 반환
     * Display의 yaw 회전도 고려하여 월드 좌표계로 변환
     * 
     * @param boneUuid Bone의 UUID
     * @return Bone의 최종 pivot 위치 (Display 위치 + yaw 회전된 translation), 없으면 null
     */
    fun getBonePivotPosition(boneUuid: String): Vector3f? {
        val display = boneToDisplay[boneUuid] ?: return null
        val location = display.location
        val translation = display.transformation?.translation ?: Vector3f(0f, 0f, 0f)
        
        // Display의 yaw 회전을 고려하여 translation을 월드 좌표계로 변환
        val yawRad = Math.toRadians(display.yaw.toDouble())
        val cosYaw = cos(yawRad).toFloat()
        val sinYaw = sin(yawRad).toFloat()
        
        // Minecraft 좌표계: yaw 0도 = +Z 방향, 90도 = -X 방향
        // Y축 기준 회전 (yaw)
        val rotatedX = translation.x * cosYaw - translation.z * sinYaw
        val rotatedZ = translation.x * sinYaw + translation.z * cosYaw
        val rotatedY = translation.y // yaw는 Y축 회전이므로 y는 변하지 않음
        
        return Vector3f(
            (location.x + rotatedX).toFloat(),
            (location.y + rotatedY).toFloat(),
            (location.z + rotatedZ).toFloat()
        )
    }
    
    /**
     * Bone의 최종 pivot 위치를 가져옴
     * Display의 위치와 transformation의 translation을 합산하여 반환
     * 
     * @param bone Bone 객체
     * @return Bone의 최종 pivot 위치 (Display 위치 + translation), 없으면 null
     */
    fun getBonePivotPosition(bone: Bone): Vector3f? {
        return getBonePivotPosition(bone.uuid)
    }
    
    /**
     * Bone의 최종 pivot 위치를 Location으로 가져옴
     * Display의 위치와 transformation의 translation을 합산하여 반환
     * Display의 yaw 회전도 고려하여 월드 좌표계로 변환
     * 
     * @param boneUuid Bone의 UUID
     * @return Bone의 최종 pivot 위치를 Location으로 반환, 없으면 null
     */
    fun getBonePivotLocation(boneUuid: String): Location? {
        val display = boneToDisplay[boneUuid] ?: return null
        val location = display.location.clone()
        val translation = display.transformation?.translation ?: Vector3f(0f, 0f, 0f)
        
        // Display의 yaw 회전을 고려하여 translation을 월드 좌표계로 변환
        val yawRad = Math.toRadians(display.yaw.toDouble())
        val cosYaw = cos(yawRad).toFloat()
        val sinYaw = sin(yawRad).toFloat()
        
        // Minecraft 좌표계: yaw 0도 = +Z 방향, 90도 = -X 방향
        // Y축 기준 회전 (yaw)
        val rotatedX = translation.x * cosYaw - translation.z * sinYaw
        val rotatedZ = translation.x * sinYaw + translation.z * cosYaw
        val rotatedY = translation.y // yaw는 Y축 회전이므로 y는 변하지 않음
        
        location.add(rotatedX.toDouble(), rotatedY.toDouble(), rotatedZ.toDouble())
        return location
    }
    
    /**
     * Bone의 최종 pivot 위치를 Location으로 가져옴
     * Display의 위치와 transformation의 translation을 합산하여 반환
     * 
     * @param bone Bone 객체
     * @return Bone의 최종 pivot 위치를 Location으로 반환, 없으면 null
     */
    fun getBonePivotLocation(bone: Bone): Location? {
        return getBonePivotLocation(bone.uuid)
    }
}

