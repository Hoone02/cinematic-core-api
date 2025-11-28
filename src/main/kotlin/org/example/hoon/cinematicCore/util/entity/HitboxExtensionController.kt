package org.example.hoon.cinematicCore.util.entity

import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import kotlin.math.max
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class HitboxExtensionConfig(
    val heightBonus: Float = 0f,
    val horizontalExtension: Float = 0f,
    val minWidth: Float = 0.6f
)

class ExtendedHitboxController(
    private val mob: Mob,
    private val config: HitboxExtensionConfig
) {
    // 원래 dimensions와 eyeHeight를 저장
    private val originalDimensions: EntityDimensions
    private val originalEyeHeight: Float

    init {
        // 처음 생성할 때 원래 값 저장
        originalDimensions = DIMENSIONS_FIELD.get(mob) as EntityDimensions
        originalEyeHeight = EYE_HEIGHT_FIELD.getFloat(mob)
    }

    fun install(setBoundingBox: (AABB) -> Unit) {
        applyExpandedHitbox(setBoundingBox)
    }

    fun refresh(setBoundingBox: (AABB) -> Unit) {
        applyExpandedHitbox(setBoundingBox)
    }

    fun clear() {
        // 원래 dimensions와 eyeHeight로 복원
        DIMENSIONS_FIELD.set(mob, originalDimensions)
        EYE_HEIGHT_FIELD.setFloat(mob, originalEyeHeight)
        
        // 원래 boundingBox로 복원
        val halfWidth = originalDimensions.width.toDouble() / 2.0
        val originalBox = AABB(
            mob.x - halfWidth,
            mob.y,
            mob.z - halfWidth,
            mob.x + halfWidth,
            mob.y + originalDimensions.height,
            mob.z + halfWidth
        )
        SET_BOUNDING_BOX_METHOD.invoke(mob, originalBox)
    }

    private fun applyExpandedHitbox(setBoundingBox: (AABB) -> Unit) {
        val extendedWidth = max(config.minWidth, originalDimensions.width + config.horizontalExtension * 2f)
        val extendedHeight = originalDimensions.height + config.heightBonus
        val extendedDimensions = EntityDimensions.scalable(extendedWidth, extendedHeight)

        DIMENSIONS_FIELD.set(mob, extendedDimensions)
        EYE_HEIGHT_FIELD.setFloat(mob, originalEyeHeight + config.heightBonus)

        val halfWidth = extendedDimensions.width.toDouble() / 2.0
        val newBox = AABB(
            mob.x - halfWidth,
            mob.y,
            mob.z - halfWidth,
            mob.x + halfWidth,
            mob.y + extendedDimensions.height,
            mob.z + halfWidth
        )
        setBoundingBox(newBox)
    }

    companion object {
        private val DIMENSIONS_FIELD = net.minecraft.world.entity.Entity::class.java.getDeclaredField("dimensions").apply {
            isAccessible = true
        }
        private val EYE_HEIGHT_FIELD = net.minecraft.world.entity.Entity::class.java.getDeclaredField("eyeHeight").apply {
            isAccessible = true
        }
        internal val SET_BOUNDING_BOX_METHOD = net.minecraft.world.entity.Entity::class.java
            .getDeclaredMethod("setBoundingBox", AABB::class.java).apply { isAccessible = true }
    }
}

private val CONTROLLERS = ConcurrentHashMap<UUID, ExtendedHitboxController>()

class HitboxExtensionHandle internal constructor(private val mob: Mob) {
    fun refresh() = mob.refreshCustomBoundingBox()
    fun clear() = mob.clearCustomBoundingBox()
}

fun Mob.setCustomBoundingBox(heightBonus: Float, horizontalExtension: Float): HitboxExtensionHandle {
    val config = HitboxExtensionConfig(heightBonus = heightBonus, horizontalExtension = horizontalExtension)
    return setCustomBoundingBox(config)
}

fun Mob.setCustomBoundingBox(config: HitboxExtensionConfig): HitboxExtensionHandle {
    // 기존 컨트롤러가 있으면 먼저 정리
    CONTROLLERS[this.uuid]?.clear()
    CONTROLLERS.remove(this.uuid)
    
    val controller = ExtendedHitboxController(this, config)
    CONTROLLERS[this.uuid] = controller
    controller.install { box -> ExtendedHitboxController.SET_BOUNDING_BOX_METHOD.invoke(this, box) }
    return HitboxExtensionHandle(this)
}

fun Mob.refreshCustomBoundingBox() {
    CONTROLLERS[this.uuid]?.refresh { box -> ExtendedHitboxController.SET_BOUNDING_BOX_METHOD.invoke(this, box) }
}

fun Mob.clearCustomBoundingBox() {
    CONTROLLERS[this.uuid]?.clear()
    CONTROLLERS.remove(this.uuid)
}

