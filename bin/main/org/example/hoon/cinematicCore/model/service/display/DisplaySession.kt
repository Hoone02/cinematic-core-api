package org.example.hoon.cinematicCore.model.service.display

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitTask
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.inventory.meta.components.CustomModelDataComponent
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.getBone
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.HITBOX_SIZE_METADATA
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.HITBOX_INTERACTION_METADATA
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.HITBOX_DISPLAY_METADATA
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.HITBOX_TELEPORT_TASK_METADATA
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.getHitboxInteraction
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.getHitboxDisplay
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.getHitboxTeleportTask
import org.example.hoon.cinematicCore.model.store.ScaledBoneCache
import org.example.hoon.cinematicCore.util.animation.AnimationController
import org.example.hoon.cinematicCore.util.animation.BoneDisplayMapper
import org.example.hoon.cinematicCore.util.entity.clearCustomBoundingBox
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventSignalManager
import org.joml.Vector3f

class DisplaySession(
    val baseEntity: LivingEntity,
    private val rotationSynchronizer: RotationSynchronizer,
    val mapper: BoneDisplayMapper,
    val animationController: AnimationController,
    val model: BlockbenchModel,
    private val scaledBoneCache: ScaledBoneCache,
    var enableDamageColorChange: Boolean = true  // 데미지 받을 때 빨간색으로 변하는 기능 활성화 여부 (기본값: true)
) {

    private val displays = mutableSetOf<ItemDisplay>()
    private var rotationTask: BukkitTask? = null
    private val originalColors = mutableMapOf<ItemDisplay, Color?>()
    // bone 교체 정보 저장: boneUuid -> (replacementModel, replacementBoneName)
    private val boneReplacements: MutableMap<String, Pair<BlockbenchModel, String>> = mutableMapOf()
    // 탑승 정보 저장: passenger -> (boneUuid, canDismount, task)
    private val mountedPassengers: MutableMap<org.bukkit.entity.Entity, Triple<String, Boolean, org.bukkit.scheduler.BukkitTask>> = mutableMapOf()

    fun track(display: ItemDisplay) {
        displays.add(display)
    }

    fun initialize() {
        rotationTask = rotationSynchronizer.start(baseEntity, displays)
        val originalHitboxHeight = baseEntity.getMetadata(HITBOX_SIZE_METADATA)
            .firstOrNull()
            ?.asFloat()
            ?: baseEntity.boundingBox.height.toFloat()
        animationController.adjustAllBoneTranslationsY(-originalHitboxHeight / 2)
        animationController.update()
    }

    fun stop(removeBaseEntity: Boolean = true) {
        rotationTask?.cancel()
        rotationTask = null

        mapper.clear()

        animationController.stop()

        // 엔티티에서 모델 정보 제거
        getHitboxInteraction(baseEntity)?.let {
            if (it.isValid) {
                it.remove()
            }
        }
        getHitboxDisplay(baseEntity)?.let {
            if (it.isValid) {
                it.remove()
            }
        }
        getHitboxTeleportTask(baseEntity)?.cancel()
        if (baseEntity.hasMetadata(HITBOX_INTERACTION_METADATA)) {
            baseEntity.removeMetadata(HITBOX_INTERACTION_METADATA, CinematicCore.instance)
        }
        if (baseEntity.hasMetadata(HITBOX_DISPLAY_METADATA)) {
            baseEntity.removeMetadata(HITBOX_DISPLAY_METADATA, CinematicCore.instance)
        }
        if (baseEntity.hasMetadata(HITBOX_TELEPORT_TASK_METADATA)) {
            baseEntity.removeMetadata(HITBOX_TELEPORT_TASK_METADATA, CinematicCore.instance)
        }
        if (baseEntity.hasMetadata(HITBOX_SIZE_METADATA)) {
            baseEntity.removeMetadata(HITBOX_SIZE_METADATA, CinematicCore.instance)
        }

        // baseEntity의 boundingBox를 원래대로 복원
        if (baseEntity is CraftEntity && baseEntity.isValid) {
            val nmsEntity = baseEntity.handle
            if (nmsEntity is net.minecraft.world.entity.Mob) {
                nmsEntity.clearCustomBoundingBox()
            }
        }

        EntityModelUtil.unregister(baseEntity)
        AnimationEventSignalManager.unregister(baseEntity)

        if (removeBaseEntity && baseEntity.isValid) {
            baseEntity.remove()
        }

        displays.forEach { display ->
            if (display.isValid) {
                display.remove()
            }
        }
        displays.clear()
        originalColors.clear()
        
        // 탑승한 모든 엔티티 정리
        mountedPassengers.values.forEach { (_, _, task) ->
            task.cancel()
        }
        mountedPassengers.clear()
    }
    
    /**
     * bone에 탑승한 엔티티 정보를 등록
     */
    fun registerMountedPassenger(
        passenger: org.bukkit.entity.Entity,
        boneUuid: String,
        canDismount: Boolean,
        task: org.bukkit.scheduler.BukkitTask
    ) {
        // 기존 탑승 정보가 있으면 태스크 취소
        mountedPassengers[passenger]?.third?.cancel()
        
        mountedPassengers[passenger] = Triple(boneUuid, canDismount, task)
    }
    
    /**
     * 탑승한 엔티티 정보 제거
     */
    fun unregisterMountedPassenger(passenger: org.bukkit.entity.Entity) {
        mountedPassengers.remove(passenger)?.third?.cancel()
    }
    
    /**
     * 탑승한 엔티티가 내릴 수 있는지 확인
     */
    fun canPassengerDismount(passenger: org.bukkit.entity.Entity): Boolean {
        return mountedPassengers[passenger]?.second ?: true
    }
    
    /**
     * 특정 엔티티가 이 세션의 bone에 탑승했는지 확인
     */
    fun isPassengerMounted(passenger: org.bukkit.entity.Entity): Boolean {
        return mountedPassengers.containsKey(passenger)
    }
    
    /**
     * 탑승한 엔티티를 강제로 내림
     */
    fun forceDismountPassenger(passenger: org.bukkit.entity.Entity): Boolean {
        val info = mountedPassengers.remove(passenger) ?: return false
        info.third.cancel()
        
        if (passenger.isValid && passenger.isInsideVehicle) {
            passenger.leaveVehicle()
        }
        return true
    }

    fun replaceBoneWithModel(
        targetBoneName: String,
        replacementModel: BlockbenchModel,
        replacementBoneName: String = targetBoneName
    ): Boolean {
        val targetBone = model.getBone(targetBoneName.lowercase()) ?: return false
        val replacementBone = replacementModel.getBone(replacementBoneName.lowercase()) ?: return false
        val display = mapper.getDisplay(targetBone) ?: return false

        val material = if (replacementModel.modelName.equals("player", ignoreCase = true)) {
            Material.PLAYER_HEAD
        } else {
            Material.LEATHER_HORSE_ARMOR
        }
        
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return false
        val component: CustomModelDataComponent = meta.customModelDataComponent
        component.strings = listOf("${replacementModel.modelName}:${replacementBone.name}")
        meta.setCustomModelDataComponent(component)
        
        // Player_Head인 경우 추가 설정
        if (material == Material.PLAYER_HEAD && meta is SkullMeta) {
            // ItemDisplayTransform 설정
            display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND
            
            // itemModel 설정 (bone 이름 사용)
            meta.itemModel = NamespacedKey.minecraft("blueprint/player_display/${replacementBone.name.lowercase()}")
        }
        
        item.itemMeta = meta
        display.setItemStack(item)

        // bone 교체 정보 저장
        boneReplacements[targetBone.uuid] = Pair(replacementModel, replacementBone.name)
        
        // ForwardKinematics에 bone 교체 정보 전달
        animationController.setBoneReplacement(targetBone.uuid, replacementModel, replacementBone.name)

        val scaleEntry = scaledBoneCache.getEntry(replacementModel.modelName, replacementBone.name)
        val baseScaleValue = if (scaleEntry != null && scaleEntry.scale > 0.0) {
            (1.0 / scaleEntry.scale).toFloat()
        } else {
            1f
        }
        val baseScale = Vector3f(baseScaleValue, baseScaleValue, baseScaleValue)

        animationController.overrideDisplayInitialScale(targetBone.uuid, baseScale)
        animationController.updateForwardKinematics()

        return true
    }
    
    /**
     * bone 교체 정보 가져오기
     */
    fun getBoneReplacement(boneUuid: String): Pair<BlockbenchModel, String>? {
        return boneReplacements[boneUuid]
    }

    /**
     * 모든 displayEntity의 색상을 빨간색으로 변경
     */
    fun setDisplaysRed() {
        displays.forEach { display ->
            if (!display.isValid) return@forEach
            val itemStack = display.itemStack ?: return@forEach
            // Leather_Horse_Armor인 경우에만 색상 변경 (Player_Head는 색상 변경 불가)
            if (itemStack.type != Material.LEATHER_HORSE_ARMOR) return@forEach
            
            val meta = itemStack.itemMeta as? LeatherArmorMeta ?: return@forEach
            // 원래 색상 저장 (없으면 null)
            if (!originalColors.containsKey(display)) {
                originalColors[display] = meta.color
            }
            // 빨간색으로 변경 (#ff6666)
            val newItemStack = itemStack.clone()
            val newMeta = newItemStack.itemMeta as? LeatherArmorMeta ?: return@forEach
            newMeta.setColor(Color.fromRGB(0xFF6666))
            newItemStack.itemMeta = newMeta
            display.setItemStack(newItemStack)
        }
    }

    /**
     * 모든 displayEntity의 색상을 원래 색상으로 복원
     */
    fun restoreDisplaysColor() {
        displays.forEach { display ->
            if (!display.isValid) return@forEach
            val itemStack = display.itemStack ?: return@forEach
            // Leather_Horse_Armor인 경우에만 색상 복원 (Player_Head는 색상 변경 불가)
            if (itemStack.type != Material.LEATHER_HORSE_ARMOR) return@forEach
            
            val originalColor = originalColors.remove(display) ?: return@forEach
            val newItemStack = itemStack.clone()
            val newMeta = newItemStack.itemMeta as? LeatherArmorMeta ?: return@forEach
            newMeta.setColor(originalColor)
            newItemStack.itemMeta = newMeta
            display.setItemStack(newItemStack)
        }
    }
}

