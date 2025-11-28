package org.example.hoon.cinematicCore.listener

import org.bukkit.Bukkit
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Trident
import org.bukkit.entity.ThrownPotion
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.metadata.FixedMetadataValue
import java.lang.reflect.Constructor
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory
import org.example.hoon.cinematicCore.model.service.display.DisplayEntityFactory.Companion.HITBOX_DAMAGE_BYPASS_METADATA
import org.example.hoon.cinematicCore.model.domain.animation.AnimationLoopMode
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.example.hoon.cinematicCore.model.service.ModelDisplaySpawner
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class HitboxListener : Listener {
    
    // baseEntity가 감지되었는지 추적하는 Set (같은 틱 내에서만 유효)
    private val processedBaseEntities = mutableSetOf<LivingEntity>()
    
    init {
        // 매 틱마다 Set 초기화
        Bukkit.getScheduler().runTaskTimer(CinematicCore.instance, Runnable {
            processedBaseEntities.clear()
        }, 0L, 1L)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onBaseEntityInteract(event: PlayerInteractEntityEvent) {
        val baseEntity = event.rightClicked as? LivingEntity ?: return
        if (!EntityModelUtil.hasModel(baseEntity)) {
            return
        }
        
        val interaction = DisplayEntityFactory.getHitboxInteraction(baseEntity) ?: return
        if (!interaction.isValid) {
            return
        }
        
        // baseEntity가 감지되었음을 표시 (Interaction 이벤트를 취소하기 위해)
        processedBaseEntities.add(baseEntity)
        
        // baseEntity 이벤트는 정상 처리되도록 둠
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    fun onInteractionInteract(event: PlayerInteractEntityEvent) {
        val interaction = event.rightClicked as? Interaction ?: return
        val baseEntity = DisplayEntityFactory.getHitboxOwner(interaction) ?: return
        if (!baseEntity.isValid) {
            return
        }

        // baseEntity가 이미 감지되었다면 Interaction 이벤트 취소
        if (processedBaseEntities.contains(baseEntity)) {
            event.isCancelled = true
            return
        }

        // Interaction 이벤트 즉시 취소 (다른 리스너들이 Interaction을 감지하지 못하도록)
        event.isCancelled = true

        // baseEntity에 대한 새로운 PlayerInteractEntityEvent 생성 및 호출
        try {
            val constructor: Constructor<PlayerInteractEntityEvent> = PlayerInteractEntityEvent::class.java
                .getDeclaredConstructor(Player::class.java, Entity::class.java, EquipmentSlot::class.java)
            constructor.isAccessible = true
            val newEvent = constructor.newInstance(event.player, baseEntity, event.hand)
            Bukkit.getPluginManager().callEvent(newEvent)
        } catch (e: Exception) {
            // 리플렉션 실패 시 대체 방법: 다음 틱에 처리
            Bukkit.getScheduler().runTask(CinematicCore.instance, Runnable {
                try {
                    val constructor: Constructor<PlayerInteractEntityEvent> = PlayerInteractEntityEvent::class.java
                        .getDeclaredConstructor(Player::class.java, Entity::class.java, EquipmentSlot::class.java)
                    constructor.isAccessible = true
                    val newEvent = constructor.newInstance(event.player, baseEntity, event.hand)
                    Bukkit.getPluginManager().callEvent(newEvent)
                } catch (ex: Exception) {
                    // 리플렉션 실패 시 무시 (다른 방법으로 처리 불가)
                }
            })
        }
    }

    @EventHandler
    fun onInteractionDamaged(event: EntityDamageByEntityEvent) {
        val interaction = event.entity as? Interaction ?: return
        val baseEntity = DisplayEntityFactory.getHitboxOwner(interaction) ?: return
        if (!baseEntity.isValid) {
            return
        }

        event.isCancelled = true
        forwardDamage(baseEntity, event)
    }

    @EventHandler
    fun onBaseEntityDamaged(event: EntityDamageEvent) {
        val living = event.entity as? LivingEntity ?: return
        if (!EntityModelUtil.hasModel(living)) {
            return
        }

        if (living.hasMetadata(HITBOX_DAMAGE_BYPASS_METADATA)) {
            living.removeMetadata(HITBOX_DAMAGE_BYPASS_METADATA, CinematicCore.instance)
            // forwardDamage에서 호출된 경우에도 색상 변경 처리
            triggerDamageColorChange(living)
            return
        }

        val projectileEvent = event as? EntityDamageByEntityEvent
        if (projectileEvent?.damager is Projectile) {
            // 투사체 데미지도 색상 변경 처리
            triggerDamageColorChange(living)
            return
        }

        // 데미지를 받으면 displayEntity들의 색상을 빨간색으로 변경
        triggerDamageColorChange(living)
    }

    /**
     * 데미지를 받았을 때 displayEntity들의 색상을 빨간색으로 변경하는 헬퍼 메서드
     */
    private fun triggerDamageColorChange(living: LivingEntity) {
        val session = EntityModelUtil.getSession(living) ?: return
        
        // 데미지 색상 변경 기능이 비활성화되어 있으면 무시
        if (!session.enableDamageColorChange) {
            return
        }
        
        session.setDisplaysRed()
        
        // 0.4초 후 원래 색상으로 복원 (0.4초 = 8틱)
        Bukkit.getScheduler().runTaskLater(CinematicCore.instance, Runnable {
            val currentSession = EntityModelUtil.getSession(living)
            if (currentSession == session && living.isValid) {
                session.restoreDisplaysColor()
            }
        }, 8L)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDismount(event: EntityDismountEvent) {
        val passenger = event.entity
        val vehicle = event.dismounted
        
        // vehicle이 모델이 적용된 엔티티인지 확인
        if (vehicle !is LivingEntity || !EntityModelUtil.hasModel(vehicle)) {
            return
        }
        
        val session = EntityModelUtil.getSession(vehicle) ?: return
        
        // 탑승 정보 확인
        if (!session.isPassengerMounted(passenger)) {
            return
        }
        
        // 내릴 수 없는 경우 내림 방지
        if (!session.canPassengerDismount(passenger)) {
            event.isCancelled = true
            
            // 다음 틱에 다시 탑승시켜서 내림 방지
            // fakeMount를 찾아서 다시 탑승
            org.bukkit.Bukkit.getScheduler().runTask(CinematicCore.instance, Runnable {
                if (!passenger.isValid || !vehicle.isValid) {
                    return@Runnable
                }
                
                // vehicle 주변의 ArmorStand를 찾아서 fakeMount 확인
                val nearbyEntities = vehicle.world.getNearbyEntities(
                    vehicle.location,
                    5.0, 5.0, 5.0
                ).filterIsInstance<org.bukkit.entity.ArmorStand>()
                
                val fakeMount = nearbyEntities.firstOrNull { armorStand ->
                    armorStand.hasMetadata("cinematiccore_vehicle") &&
                    armorStand.hasMetadata("cinematiccore_passenger") &&
                    armorStand.getMetadata("cinematiccore_vehicle").firstOrNull()?.asString() == vehicle.uniqueId.toString() &&
                    armorStand.getMetadata("cinematiccore_passenger").firstOrNull()?.asString() == passenger.uniqueId.toString()
                }
                
                // fakeMount를 찾았고 passenger가 내려갔다면 다시 탑승
                if (fakeMount != null && fakeMount.isValid && !passenger.isInsideVehicle) {
                    fakeMount.addPassenger(passenger)
                }
            })
        }
    }

    @EventHandler
    fun onBaseEntityDeath(event: EntityDeathEvent) {
        val living = event.entity as? LivingEntity ?: return
        val modelInfo = EntityModelUtil.getModelInfo(living) ?: return
        val session = modelInfo.session

        // death 애니메이션 재생 전에 빨간색 상태를 원래 색상으로 복원
        session.restoreDisplaysColor()

        val deathAnimation = modelInfo.model.animations.firstOrNull {
            it.name.equals("death", ignoreCase = true)
        }

        if (deathAnimation == null) {
            session.stop(removeBaseEntity = false)
            return
        }

        // baseEntity가 죽었음을 표시 (death 애니메이션이 재생되도록)
        session.animationController.markBaseEntityDead()
        
        // death 애니메이션 재생 (중단 불가능)
        session.animationController.playAnimation(deathAnimation, 1.0f, interruptible = false)

        val delayTicks = computeAnimationDurationTicks(deathAnimation)
        Bukkit.getScheduler().runTaskLater(CinematicCore.instance, Runnable {
            val currentSession = EntityModelUtil.getSession(living) ?: return@Runnable
            if (currentSession == session) {
                currentSession.stop(removeBaseEntity = false)
            }
        }, delayTicks)
    }

    private fun computeAnimationDurationTicks(animation: org.example.hoon.cinematicCore.model.domain.animation.BlockbenchAnimation): Long {
        val lengthSeconds = animation.length.coerceAtLeast(0f)
        if (lengthSeconds == 0f && animation.loopMode != AnimationLoopMode.LOOP) {
            return 1L
        }

        val ticks = ceil(lengthSeconds * TICKS_PER_SECOND).toLong().coerceAtLeast(1L)
        return if (animation.loopMode == AnimationLoopMode.LOOP && lengthSeconds == 0f) {
            1L
        } else {
            ticks
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val interaction = event.hitEntity as? Interaction ?: return
        val baseEntity = DisplayEntityFactory.getHitboxOwner(interaction) ?: return
        if (!baseEntity.isValid) {
            return
        }

        forwardProjectileDamage(baseEntity, event.entity)
    }

    private fun forwardDamage(baseEntity: LivingEntity, event: EntityDamageByEntityEvent) {
        val plugin = CinematicCore.instance
        baseEntity.setMetadata(
            HITBOX_DAMAGE_BYPASS_METADATA,
            FixedMetadataValue(plugin, true)
        )

        val damager = event.damager
        when (damager) {
            is LivingEntity -> damager.attack(baseEntity)
            is Projectile -> forwardProjectileDamage(baseEntity, damager, event.finalDamage)
            else -> baseEntity.damage(event.finalDamage, damager)
        }
    }

    private fun forwardProjectileDamage(
        baseEntity: LivingEntity,
        projectile: Projectile,
        fallbackDamage: Double? = null
    ) {
        val plugin = CinematicCore.instance
        baseEntity.setMetadata(
            HITBOX_DAMAGE_BYPASS_METADATA,
            FixedMetadataValue(plugin, true)
        )

        val shooter = projectile.shooter as? LivingEntity
        if (projectile is ThrownPotion) {
            applyPotionEffects(baseEntity, projectile)
            projectile.remove()
            return
        }

        val damage = when (projectile) {
            is AbstractArrow -> projectile.damage
            is Trident -> projectile.damage
            else -> fallbackDamage ?: projectile.velocity.length()
        }
        baseEntity.damage(damage, shooter ?: projectile)
        projectile.remove()
    }

    @EventHandler
    fun onPotionSplash(event: PotionSplashEvent) {
        val potion = event.potion
        val interactions = potion.world.getNearbyEntities(potion.location, SPLASH_MAX_DISTANCE, SPLASH_MAX_DISTANCE, SPLASH_MAX_DISTANCE)
            .filterIsInstance<Interaction>()
        val processedBases = mutableSetOf<LivingEntity>()

        // Interaction 주변에 있는 베이스 엔티티 처리
        interactions.forEach { interaction ->
            val base = DisplayEntityFactory.getHitboxOwner(interaction) ?: return@forEach
            if (!base.isValid || !processedBases.add(base)) {
                return@forEach
            }
            val intensity = intensityFromDistance(potion, base)
            applyPotionEffects(base, potion, intensity)
        }

        // 실제 베이스 엔티티가 포션 범위에 들어온 경우
        event.affectedEntities
            .filterIsInstance<LivingEntity>()
            .filter { EntityModelUtil.hasModel(it) }
            .forEach { living ->
                val intensity = event.getIntensity(living).toDouble()
                if (processedBases.add(living)) {
                    applyPotionEffects(living, potion, intensity)
                }
            }
    }

    private fun intensityFromDistance(potion: ThrownPotion, base: LivingEntity): Double {
        val distance = potion.location.distance(base.location)
        if (distance > SPLASH_MAX_DISTANCE) {
            return 0.0
        }
        val normalized = 1.0 - (distance / SPLASH_MAX_DISTANCE)
        return normalized.coerceIn(0.0, 1.0)
    }

    private fun applyPotionEffects(target: LivingEntity, potion: ThrownPotion, intensity: Double = 1.0) {
        val shooter = potion.shooter as? LivingEntity
        val effects = potion.effects
        if (effects.isEmpty()) {
            return
        }

        effects.forEach { effect ->
            val type = effect.type ?: return@forEach
            if (type.isInstant) {
                applyInstantPotionEffect(target, shooter ?: potion, type, effect.amplifier)
            } else {
                val duration = max(1, (effect.duration * intensity).toInt())
                val applied = PotionEffect(
                    type,
                    duration,
                    effect.amplifier,
                    effect.isAmbient,
                    effect.hasParticles(),
                    effect.hasIcon()
                )
                target.addPotionEffect(applied, true)
            }
        }
    }

    private fun applyInstantPotionEffect(
        target: LivingEntity,
        source: Entity,
        type: PotionEffectType,
        amplifier: Int
    ) {
        when (type) {
            PotionEffectType.INSTANT_DAMAGE -> {
                val amount = 6.0 * (amplifier + 1)
                target.damage(amount, source)
            }

            PotionEffectType.INSTANT_HEALTH -> {
                val amount = 4.0 * (amplifier + 1)
                target.health = min(target.maxHealth, target.health + amount)
            }

            PotionEffectType.SATURATION -> {
                val player = target as? Player ?: return
                val amount = (2 * (amplifier + 1))
                player.foodLevel = min(20, player.foodLevel + amount)
                player.saturation = min(20f, player.saturation + amount.toFloat())
            }

            else -> {
                // 기타 즉발 포션은 현재 지원하지 않음
            }
        }
    }
}

private const val SPLASH_MAX_DISTANCE = 4.0
private const val TICKS_PER_SECOND = 20.0

