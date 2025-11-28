package org.example.hoon.cinematicCore.model.service.display

import io.papermc.paper.entity.TeleportFlag
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.inventory.meta.components.CustomModelDataComponent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.getChildElements
import org.example.hoon.cinematicCore.model.store.ScaledBoneCache
import org.example.hoon.cinematicCore.util.animation.BoneDisplayMapper
import org.example.hoon.cinematicCore.util.display.updateTransformation
import org.example.hoon.cinematicCore.util.entity.setCustomBoundingBox
import org.example.hoon.cinematicCore.util.math.MathUtil
import java.util.UUID

class DisplayEntityFactory(
    private val plugin: Plugin,
    private val model: BlockbenchModel,
    private val scaledBoneCache: ScaledBoneCache
) {
    companion object {
        const val HITBOX_SIZE_METADATA = "cinematiccore:hitbox_size"
        const val HITBOX_INTERACTION_METADATA = "cinematiccore:hitbox_interaction"
        const val HITBOX_DISPLAY_METADATA = "cinematiccore:hitbox_display"
        const val HITBOX_TELEPORT_TASK_METADATA = "cinematiccore:hitbox_teleport_task"
        const val HITBOX_OWNER_METADATA = "cinematiccore:hitbox_owner"
        const val HITBOX_DAMAGE_BYPASS_METADATA = "cinematiccore:hitbox_damage_bypass"

        fun getHitboxInteraction(entity: LivingEntity): Interaction? {
            return entity.getMetadata(HITBOX_INTERACTION_METADATA)
                .firstOrNull()
                ?.value() as? Interaction
        }

        fun getHitboxDisplay(entity: LivingEntity): ItemDisplay? {
            return entity.getMetadata(HITBOX_DISPLAY_METADATA)
                .firstOrNull()
                ?.value() as? ItemDisplay
        }

        fun getHitboxTeleportTask(entity: LivingEntity): org.bukkit.scheduler.BukkitTask? {
            return entity.getMetadata(HITBOX_TELEPORT_TASK_METADATA)
                .firstOrNull()
                ?.value() as? org.bukkit.scheduler.BukkitTask
        }

        fun getHitboxOwner(interaction: Interaction): LivingEntity? {
            val ownerId = interaction.getMetadata(HITBOX_OWNER_METADATA)
                .firstOrNull()
                ?.asString()
                ?: return null
            val uuid = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return null
            return Bukkit.getEntity(uuid) as? LivingEntity
        }
    }

    fun create(
        bone: Bone,
        location: Location,
        base: LivingEntity,
        mapper: BoneDisplayMapper,
        player: Player?
    ): ItemDisplay {
        val display = location.world.spawn(location, ItemDisplay::class.java)
        display.interpolationDelay = 0
        display.teleportDuration = 1
        // player 모델일 때만 초기 rotation 설정 (정면을 보도록)
        if (model.modelName.equals("player", ignoreCase = true)) {
            display.setRotation(0f, 0f)
        }
        // visibility가 false면 AIR로 설정
        if (!bone.visibility) {
            display.setItemStack(ItemStack(Material.AIR))
        } else {
            applyCustomModelData(display, bone, player)
        }
        base.addPassenger(display)
        mapper.map(bone.uuid, display)
        hideIfHitbox(bone, player, display, base)
        applyScale(display, bone)
        // player 모델일 때만 bone 이름에 따른 translation y값 설정
        if (model.modelName.equals("player", ignoreCase = true)) {
            val translationY = getTranslationYByBoneName(bone.name)
            display.updateTransformation {
                translation {
                    x = 0f
                    y = translationY
                    z = 0f
                }
            }
        }
        return display
    }

    /**
     * bone 이름에 따른 translation y값 반환
     */
    private fun getTranslationYByBoneName(boneName: String): Float {
        return when (boneName.lowercase()) {
            "head" -> 0f
            "right_arm" -> -6144.0f  // right_forearm과 교체
            "left_arm" -> -7168.0f
            "torso" -> -3072.0f
            "right_leg" -> -9216.0f
            "left_leg" -> -10240.0f
            "right_forearm" -> -1024.0f  // right_arm과 교체
            "left_forearm" -> -2048.0f
            "waist" -> -8192.0f
            "lower_right_leg" -> -4096.0f
            "lower_left_leg" -> -5120.0f
            else -> -0.0f // 기본값
        }
    }

    /**
     * player 모델에서 player_head를 사용해야 하는 bone인지 확인
     */
    private fun isPlayerHeadBone(boneName: String): Boolean {
        return when (boneName.lowercase()) {
            "head",
            "right_arm",
            "left_arm",
            "torso",
            "right_leg",
            "left_leg",
            "right_forearm",
            "left_forearm",
            "waist",
            "lower_right_leg",
            "lower_left_leg" -> true
            else -> false
        }
    }

    private fun applyCustomModelData(display: ItemDisplay, bone: Bone, player: Player?) {
        val material = if (model.modelName.equals("player", ignoreCase = true) && isPlayerHeadBone(bone.name)) {
            Material.PLAYER_HEAD
        } else {
            Material.LEATHER_HORSE_ARMOR
        }
        
        val item = ItemStack(material)
        val meta = item.itemMeta
        val component: CustomModelDataComponent = meta.customModelDataComponent
        component.strings = listOf("${model.modelName}:${bone.name}")
        meta.setCustomModelDataComponent(component)
        
        // Player_Head인 경우 추가 설정
        if (material == Material.PLAYER_HEAD && meta is SkullMeta) {
            // ItemDisplayTransform 설정
            display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND
            
            // itemModel 설정 (bone 이름 사용)
            meta.itemModel = NamespacedKey.minecraft("blueprint/player_display/${bone.name.lowercase()}")
            
            // owningPlayer 설정 (player가 있는 경우)
            if (player != null) {
                meta.owningPlayer = player
            }
        }
        
        // 하얀색 염색 적용 (Leather_Horse_Armor인 경우에만)
        if (meta is LeatherArmorMeta) {
            meta.setColor(Color.WHITE)
        }
        
        item.itemMeta = meta
        display.setItemStack(item)
    }

    private fun hideIfHitbox(bone: Bone, player: Player?, display: ItemDisplay, base: LivingEntity) {
        val isHitbox = bone.name.contains("hitbox", ignoreCase = true)
        if (!isHitbox) {
            return
        }
        display.setItemStack(ItemStack(Material.AIR))
        if (base == player) return

        val hitboxElements = model.getChildElements(bone)
        if (hitboxElements.isEmpty()) {
            return
        }

        val hitboxSize: Pair<Double, Double> = hitboxElements.firstOrNull()?.let { element ->
            val from = element.from
            val to = element.to
            MathUtil.getHeightAndWidth(
                from.x, from.y, from.z,
                to.x, to.y, to.z
            )
        } ?: return

        val hitboxHeight = hitboxSize.first / 16
        val hitboxWidth = hitboxSize.second / 16

        if (!base.hasMetadata(HITBOX_SIZE_METADATA)) {
            val originalHeight = base.boundingBox.height.toFloat()
            base.setMetadata(HITBOX_SIZE_METADATA, FixedMetadataValue(plugin, originalHeight))
        }

        val display = base.world.spawn(base.location, ItemDisplay::class.java)
        val entity = base as CraftEntity
        val nms = entity.handle
        val mob = nms as net.minecraft.world.entity.Mob
        mob.setCustomBoundingBox((hitboxHeight - base.boundingBox.height).toFloat(), (hitboxWidth / 2).toFloat())
        getHitboxInteraction(base)?.takeIf { it.isValid }?.remove()
        getHitboxDisplay(base)?.takeIf { it.isValid }?.remove()
        val interaction = base.world.spawn(base.location, Interaction::class.java)
        interaction.interactionHeight = (hitboxHeight + 0.5).toFloat()
        interaction.interactionWidth = (hitboxWidth + 0.5).toFloat()
        base.setMetadata(HITBOX_INTERACTION_METADATA, FixedMetadataValue(plugin, interaction))
        base.setMetadata(HITBOX_DISPLAY_METADATA, FixedMetadataValue(plugin, display))
        interaction.setMetadata(
            HITBOX_OWNER_METADATA,
            FixedMetadataValue(plugin, base.uniqueId.toString())
        )
        display.addPassenger(interaction)
        val teleportTask = Bukkit.getScheduler().runTaskTimer(CinematicCore.instance, Runnable{
            if (!display.isValid || !base.isValid) {
                return@Runnable
            }
            display.teleport(entity.location, TeleportFlag.EntityState.RETAIN_PASSENGERS)
        },0,1)
        base.setMetadata(HITBOX_TELEPORT_TASK_METADATA, FixedMetadataValue(plugin, teleportTask))
    }

    private fun applyScale(display: ItemDisplay, bone: Bone) {
        val cacheEntry = scaledBoneCache.getEntry(model.modelName, bone.name)
        if (cacheEntry != null && cacheEntry.scale > 0.0) {
            val inverseScale = (1.0 / cacheEntry.scale).toFloat()
            display.updateTransformation {
                scale {
                    x = inverseScale
                    y = inverseScale
                    z = inverseScale
                }
            }
        }
    }
}

