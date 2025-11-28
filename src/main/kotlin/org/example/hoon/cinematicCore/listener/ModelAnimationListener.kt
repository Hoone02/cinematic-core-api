package org.example.hoon.cinematicCore.listener

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import java.util.UUID

/**
 * 모델이 적용된 엔티티의 이동 상태를 감지하여
 * idle / walk / run 애니메이션을 자동으로 재생하는 리스너.
 *
 * - idle: 정지 상태
 * - walk: 이동 중
 * - run: 플레이어가 전력 질주 중일 때
 *
 * death 애니메이션은 HitboxListener에서 이미 처리하므로 제외한다.
 */
class ModelAnimationListener : Listener {

    private val animationStates = mutableMapOf<UUID, AnimationState>()
    private val lastLocations = mutableMapOf<UUID, Location>()
    // 상태 변경 안정화: 연속된 틱에서의 이동 상태 기록 (빈번한 전환 방지)
    private val movementHistory = mutableMapOf<UUID, MutableList<Boolean>>()

    init {
        object : BukkitRunnable() {
            override fun run() {
                updateTrackedEntities()
            }
        }.runTaskTimer(CinematicCore.instance, 0L, 1L)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        cleanup(event.player.uniqueId)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        cleanup(event.entity.uniqueId)
    }

    @EventHandler
    fun on(event: PlayerInteractEvent) {
        val player = event.player
        
        // 다이아몬드를 우클릭했는지 확인
        if (event.action != Action.LEFT_CLICK_AIR &&
            event.action != Action.LEFT_CLICK_BLOCK) {
            return
        }
        
        val item = event.item ?: return
        if (item.type != Material.DIAMOND) {
            return
        }
        
        // 플레이어에게 모델이 적용되어 있는지 확인
        val modelInfo = EntityModelUtil.getModelInfo(player) ?: return
        val session = modelInfo.session
        val model = modelInfo.model
        
        // attack1 애니메이션 찾기
        val attack1Animation = model.animations.firstOrNull {
            it.name.equals("attack1", ignoreCase = true)
        } ?: return
        session.animationController.stopAnimation()
        // attack1 애니메이션 재생
        session.animationController.playAnimation(attack1Animation, 0.3f, false)
    }

    private fun updateTrackedEntities() {
        val registered = EntityModelUtil.getAllEntityUuids()
        cleanupMissingEntities(registered)

        registered.forEach { uuid ->
            val entity = Bukkit.getEntity(uuid) as? LivingEntity ?: return@forEach
            if (!entity.isValid) {
                cleanup(uuid)
                return@forEach
            }
            updateAnimationState(entity)
        }
    }

    private fun updateAnimationState(entity: LivingEntity) {
        val modelInfo = EntityModelUtil.getModelInfo(entity) ?: return
        val session = modelInfo.session
        val model = modelInfo.model
        val uuid = entity.uniqueId

        val targetState = determineState(entity, uuid)
        val currentState = animationStates[uuid]
        if (targetState == currentState) {
            return
        }
        
        // 애니메이션이 재생 중이고 끊기면 안 되는 경우 무시
        // (예: attack1 같은 공격 애니메이션이 재생 중일 때)
        val controller = session.animationController
        if (!controller.isAnimationPlaying() || 
            (controller.currentAnimation() != null && 
             !controller.currentAnimation()!!.name.equals("idle", ignoreCase = true) &&
             !controller.currentAnimation()!!.name.equals("walk", ignoreCase = true) &&
             !controller.currentAnimation()!!.name.equals("run", ignoreCase = true))) {
            // idle/walk/run 외의 애니메이션(예: attack1)이 재생 중이면 자동 전환 무시
            // 단, 같은 애니메이션으로 전환하려는 경우는 무시하지 않음
            if (targetState.animationName.equals(controller.currentAnimation()?.name?.trim(), ignoreCase = true)) {
                animationStates[uuid] = targetState
                return
            }
            // 다른 애니메이션이 재생 중이면 상태만 업데이트하고 애니메이션 전환은 하지 않음
            return
        }

        val animation = model.animations.firstOrNull {
            it.name.equals(targetState.animationName, ignoreCase = true)
        } ?: return

        session.animationController.playAnimation(animation, 1.0f)
        animationStates[uuid] = targetState
    }

    private fun determineState(entity: LivingEntity, uuid: UUID): AnimationState {
        val currentLocation = entity.location.clone()
        val previous = lastLocations[uuid]

        // 성능 최적화: 실제 X, Z축 이동만 감지 (시선 회전은 무시)
        // 시선만 돌리면 yaw/pitch만 변하고 위치는 변하지 않으므로, 위치 차이만 확인
        val moved = previous != null &&
                previous.world?.uid == currentLocation.world?.uid && run {
            // Y축은 수직 이동(점프/낙하)이므로 애니메이션 전환에 영향 없음
            val dx = currentLocation.x - previous.x
            val dz = currentLocation.z - previous.z
            val horizontalDistanceSq = dx * dx + dz * dz
            horizontalDistanceSq > MOVEMENT_THRESHOLD
        }

        lastLocations[uuid] = currentLocation
        
        // 상태 변경 안정화: 최근 N틱의 이동 상태를 기록하여 빈번한 전환 방지
        val history = movementHistory.getOrPut(uuid) { mutableListOf() }
        history.add(moved)
        
        // 최근 HISTORY_SIZE틱만 유지
        if (history.size > HISTORY_SIZE) {
            history.removeAt(0)
        }
        
        // 히스테리시스: 이동하려면 최근 N틱 중 절반 이상 이동해야 함
        // 정지하려면 최근 N틱 중 절반 이상 정지해야 함
        val stableMoved = if (history.size >= HISTORY_SIZE) {
            val moveCount = history.count { it }
            // 이동으로 판단하려면 최근 N틱 중 절반 이상 이동
            moveCount >= HISTORY_SIZE / 2
        } else {
            // 기록이 부족하면 현재 상태만 사용
            moved
        }

        if (!stableMoved) {
            return AnimationState.IDLE
        }

        if (entity is Player && entity.isSprinting) {
            return AnimationState.RUN
        }

        return AnimationState.WALK
    }

    private fun cleanupMissingEntities(registered: Set<UUID>) {
        val iterator = animationStates.keys.iterator()
        while (iterator.hasNext()) {
            val uuid = iterator.next()
            if (!registered.contains(uuid)) {
                iterator.remove()
                lastLocations.remove(uuid)
            }
        }
    }

    private fun cleanup(uuid: UUID) {
        animationStates.remove(uuid)
        lastLocations.remove(uuid)
        movementHistory.remove(uuid)
    }

    private enum class AnimationState(val animationName: String) {
        IDLE("idle"),
        WALK("walk"),
        RUN("run")
    }

    companion object {
        // 시선 회전만으로는 애니메이션이 전환되지 않도록 임계값 증가
        // 실제로 한 틱에 이동할 수 있는 최소 거리보다 약간 크게 설정
        private const val MOVEMENT_THRESHOLD = 0.001 // 0.0001 -> 0.001 (10배 증가)
        // 상태 변경 안정화: 최근 N틱의 이동 상태를 확인하여 빈번한 전환 방지
        private const val HISTORY_SIZE = 3 // 최근 3틱의 이동 상태 확인
    }
}


