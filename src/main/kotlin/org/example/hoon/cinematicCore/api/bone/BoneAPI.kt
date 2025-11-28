package org.example.hoon.cinematicCore.api.bone

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.getBone
import org.example.hoon.cinematicCore.model.domain.getChildBones
import org.example.hoon.cinematicCore.model.domain.getParentBone
import org.example.hoon.cinematicCore.model.domain.getParentBoneChain
import org.example.hoon.cinematicCore.model.domain.getTags
import org.example.hoon.cinematicCore.model.loader.ModelFileLoader
import org.bukkit.scheduler.BukkitTask
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.util.entity.PassengerPositionManager
import java.io.File

/**
 * Bone 관련 기능을 제공하는 API 클래스
 */
object BoneAPI {
    /**
     * 플러그인 인스턴스를 가져옵니다.
     * CinematicCore가 플러그인으로 설치되어 있으면 그것을 사용하고,
     * 라이브러리로 사용 중이면 이 클래스를 로드한 플러그인을 사용합니다.
     */
    private val plugin: Plugin
        get() {
            // CinematicCore가 플러그인으로 설치되어 있으면 사용
            return try {
                CinematicCore.instance
            } catch (e: UninitializedPropertyAccessException) {
                // CinematicCore가 초기화되지 않았으면 라이브러리로 사용 중
                // 이 클래스를 로드한 플러그인 사용
                try {
                    JavaPlugin.getProvidingPlugin(BoneAPI::class.java)
                } catch (e2: IllegalStateException) {
                    throw IllegalStateException("CinematicCore API를 사용하려면 플러그인 컨텍스트가 필요합니다. JavaPlugin.getProvidingPlugin()이 실패했습니다.", e2)
                }
            }
        }
    private val passengerManager: PassengerPositionManager
        get() = PassengerPositionManager(plugin)
    
    /**
     * 엔티티에 passenger를 커스텀 오프셋으로 탑승시킵니다 (PassengerPositionManager.mountWithOffset와 동일)
     * 
     * @param vehicle 탈것 엔티티
     * @param passenger 탑승할 엔티티
     * @param forward 전방 오프셋 (양수: 앞, 음수: 뒤) - vehicle 방향 기준
     * @param up 상하 오프셋 (양수: 위, 음수: 아래) - 절대 Y축
     * @param right 좌우 오프셋 (양수: 우측, 음수: 좌측) - vehicle 방향 기준
     * @param rotateWithVehicle vehicle 방향에 따라 오프셋도 회전할지 여부 (기본값: false, 절대 오프셋)
     * @param boneUuid bone의 UUID (지정하면 해당 bone의 pivot 위치를 탑승 위치로 사용)
     * @param canDismount 내릴 수 있는지 여부 (기본값: true, false로 설정하면 자동으로 내려가지 않음)
     * @return 위치 업데이트 태스크 (필요시 취소 가능)
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val player = // 플레이어
     * val boneUuid = // bone의 UUID
     * 
     * val task = BoneAPI.mountWithOffset(
     *     vehicle = entity,
     *     passenger = player,
     *     forward = 0.5,
     *     up = 0.2,
     *     right = 0.0,
     *     rotateWithVehicle = true,
     *     boneUuid = boneUuid,
     *     canDismount = false
     * )
     * ```
     */
    fun mountWithOffset(
        vehicle: Entity,
        passenger: Entity,
        forward: Double = 0.0,
        up: Double = 0.0,
        right: Double = 0.0,
        rotateWithVehicle: Boolean = false,
        boneUuid: String? = null,
        canDismount: Boolean = true
    ): BukkitTask? {
        if (!vehicle.isValid || !passenger.isValid) return null
        if (vehicle !is LivingEntity) return null
        
        // PassengerPositionManager를 사용하여 탑승
        val task = passengerManager.mountWithOffset(
            vehicle = vehicle,
            passenger = passenger,
            forward = forward,
            up = up,
            right = right,
            rotateWithVehicle = rotateWithVehicle,
            boneUuid = boneUuid
        )
        
        // boneUuid가 있고 모델이 적용된 엔티티인 경우 탑승 정보를 세션에 등록
        if (boneUuid != null && EntityModelUtil.hasModel(vehicle)) {
            val session = EntityModelUtil.getSession(vehicle)
            if (session != null) {
                session.registerMountedPassenger(passenger, boneUuid, canDismount, task)
            }
        }
        
        return task
    }
    
    /**
     * bone 이름으로 bone UUID를 가져와서 mountWithOffset를 호출하는 헬퍼 함수
     * 
     * @param vehicle 탈것 엔티티 (모델이 적용된 엔티티)
     * @param boneName 탑승시킬 bone의 이름
     * @param passenger 탑승할 엔티티
     * @param forward 전방 오프셋 (양수: 앞, 음수: 뒤) - vehicle 방향 기준
     * @param up 상하 오프셋 (양수: 위, 음수: 아래) - 절대 Y축
     * @param right 좌우 오프셋 (양수: 우측, 음수: 좌측) - vehicle 방향 기준
     * @param rotateWithVehicle vehicle 방향에 따라 오프셋도 회전할지 여부 (기본값: false, 절대 오프셋)
     * @param canDismount 내릴 수 있는지 여부 (기본값: true, false로 설정하면 자동으로 내려가지 않음)
     * @return 위치 업데이트 태스크 (필요시 취소 가능), 실패 시 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val player = // 플레이어
     * 
     * val task = BoneAPI.mountWithOffsetByBoneName(
     *     vehicle = entity,
     *     boneName = "head",
     *     passenger = player,
     *     forward = 0.5,
     *     up = 0.2,
     *     right = 0.0,
     *     rotateWithVehicle = true,
     *     canDismount = false
     * )
     * ```
     */
    fun mountWithOffsetByBoneName(
        vehicle: Entity,
        boneName: String,
        passenger: Entity,
        forward: Double = 0.0,
        up: Double = 0.0,
        right: Double = 0.0,
        rotateWithVehicle: Boolean = false,
        canDismount: Boolean = true
    ): BukkitTask? {
        if (!vehicle.isValid || !passenger.isValid) return null
        if (vehicle !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(vehicle) ?: return null
        val model = EntityModelUtil.getModel(vehicle) ?: return null
        
        val bone = model.getBone(boneName.lowercase()) ?: return null
        val boneUuid = bone.uuid
        
        return mountWithOffset(
            vehicle = vehicle,
            passenger = passenger,
            forward = forward,
            up = up,
            right = right,
            rotateWithVehicle = rotateWithVehicle,
            boneUuid = boneUuid,
            canDismount = canDismount
        )
    }
    
    /**
     * 탑승한 엔티티를 강제로 내립니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param passenger 내릴 엔티티
     * @return 내림 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val player = // 플레이어
     * BoneAPI.dismountEntityFromBone(entity, player)
     * ```
     */
    fun dismountEntityFromBone(entity: Entity, passenger: Entity): Boolean {
        if (!entity.isValid || !passenger.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        return session.forceDismountPassenger(passenger)
    }
    
    /**
     * 엔티티가 특정 bone에 탑승했는지 확인합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param passenger 확인할 엔티티
     * @return 탑승 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val player = // 플레이어
     * if (BoneAPI.isEntityMountedOnBone(entity, player)) {
     *     println("플레이어가 탑승 중입니다")
     * }
     * ```
     */
    fun isEntityMountedOnBone(entity: Entity, passenger: Entity): Boolean {
        if (!entity.isValid || !passenger.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        return session.isPassengerMounted(passenger)
    }
    
    /**
     * 탑승한 엔티티가 내릴 수 있는지 확인합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param passenger 확인할 엔티티
     * @return 내릴 수 있는지 여부 (탑승하지 않았으면 null)
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val player = // 플레이어
     * val canDismount = BoneAPI.canPassengerDismount(entity, player)
     * canDismount?.let {
     *     if (it) {
     *         println("내릴 수 있습니다")
     *     } else {
     *         println("내릴 수 없습니다")
     *     }
     * }
     * ```
     */
    fun canPassengerDismount(entity: Entity, passenger: Entity): Boolean? {
        if (!entity.isValid || !passenger.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        
        if (!session.isPassengerMounted(passenger)) {
            return null
        }
        
        return session.canPassengerDismount(passenger)
    }
    
    /**
     * 특정 bone을 다른 모델의 bone으로 교체합니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param targetBoneName 교체할 bone의 이름
     * @param replacementModelName 교체할 모델의 이름
     * @param replacementBoneName 교체할 bone의 이름 (기본값: targetBoneName과 동일)
     * @return 교체 성공 여부
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * BoneAPI.replaceBone(entity, "head", "player_battle", "head")
     * ```
     */
    fun replaceBone(
        entity: Entity,
        targetBoneName: String,
        replacementModelName: String,
        replacementBoneName: String = targetBoneName
    ): Boolean {
        if (!entity.isValid) return false
        if (entity !is LivingEntity) return false
        
        val session = EntityModelUtil.getSession(entity) ?: return false
        
        // 교체할 모델 로드
        val modelsDir = File(plugin.dataFolder, "models/")
        val modelLoader = ModelFileLoader(modelsDir)
        val replacementModel = modelLoader.loadModelByName(replacementModelName) ?: return false
        
        // bone 교체 실행
        return session.replaceBoneWithModel(
            targetBoneName = targetBoneName.lowercase(),
            replacementModel = replacementModel,
            replacementBoneName = replacementBoneName.lowercase()
        )
    }
    
    /**
     * 특정 bone의 위치를 Location으로 가져옵니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param boneName 위치를 가져올 bone의 이름
     * @return bone의 pivot 위치 (Location), 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val location = BoneAPI.getBoneLocation(entity, "head")
     * location?.let { 
     *     // bone 위치 사용
     * }
     * ```
     */
    fun getBoneLocation(entity: Entity, boneName: String): Location? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        val model = EntityModelUtil.getModel(entity) ?: return null
        
        val bone = model.getBone(boneName.lowercase()) ?: return null
        
        return session.mapper.getBonePivotLocation(bone.uuid)
    }
    
    /**
     * bone의 태그를 가져옵니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param boneName 태그를 가져올 bone의 이름
     * @return bone의 태그 리스트 (태그가 없으면 빈 리스트)
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val tags = BoneAPI.getBoneTags(entity, "h-head")
     * // tags = ["h"]
     * ```
     */
    fun getBoneTags(entity: Entity, boneName: String): List<String> {
        if (!entity.isValid) return emptyList()
        if (entity !is LivingEntity) return emptyList()
        
        val model = EntityModelUtil.getModel(entity) ?: return emptyList()
        val bone = model.getBone(boneName.lowercase()) ?: return emptyList()
        
        return bone.getTags()
    }
    
    /**
     * bone의 계층 구조를 가져옵니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param boneName 계층 구조를 가져올 bone의 이름
     * @return bone의 계층 구조 정보, 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val hierarchy = BoneAPI.getBoneHierarchy(entity, "head")
     * hierarchy?.let {
     *     println("부모: ${it.parent?.name}")
     *     println("자식: ${it.children.map { it.name }}")
     * }
     * ```
     */
    fun getBoneHierarchy(entity: Entity, boneName: String): BoneHierarchy? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val model = EntityModelUtil.getModel(entity) ?: return null
        val bone = model.getBone(boneName.lowercase()) ?: return null
        
        val parent = model.getParentBone(bone)
        val parents = model.getParentBoneChain(bone)
        val children = model.getChildBones(bone)
        
        return BoneHierarchy(
            bone = bone,
            parent = parent,
            parents = parents,
            children = children
        )
    }
    
    /**
     * bone의 디스플레이 엔티티를 가져옵니다
     * 
     * @param entity 모델이 적용된 엔티티
     * @param boneName 디스플레이 엔티티를 가져올 bone의 이름
     * @return bone의 ItemDisplay 엔티티, 없으면 null
     * 
     * @example
     * ```kotlin
     * val entity = // 모델이 적용된 엔티티
     * val display = BoneAPI.getBoneDisplay(entity, "head")
     * display?.let {
     *     // display 엔티티 조작
     * }
     * ```
     */
    fun getBoneDisplay(entity: Entity, boneName: String): ItemDisplay? {
        if (!entity.isValid) return null
        if (entity !is LivingEntity) return null
        
        val session = EntityModelUtil.getSession(entity) ?: return null
        val model = EntityModelUtil.getModel(entity) ?: return null
        
        val bone = model.getBone(boneName.lowercase()) ?: return null
        
        return session.mapper.getDisplay(bone)
    }
}

