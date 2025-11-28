package org.example.hoon.cinematicCore.listener

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.util.animation.BoneDisplayMapper
import java.util.UUID

/**
 * disguise된 플레이어의 Lower_LeftArm bone 위치에 파티클을 계속 재생하는 리스너
 */
class BoneParticleListener : Listener {
    
    private val activeTasks = mutableMapOf<UUID, BukkitTask>()
    
    init {
        // 주기적으로 disguise된 플레이어들을 확인하고 파티클 재생
        object : BukkitRunnable() {
            override fun run() {
                updateParticles()
            }
        }.runTaskTimer(CinematicCore.instance, 0L, 1L) // 매 틱마다 실행
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // 플레이어가 나가면 태스크 정리
        cleanupPlayer(event.player.uniqueId)
    }
    
    private fun updateParticles() {
        val registered = EntityModelUtil.getAllEntityUuids()
        
        // 등록된 엔티티 중 플레이어만 필터링
        registered.forEach { uuid ->
            val entity = Bukkit.getEntity(uuid) as? Player ?: return@forEach
            if (!entity.isValid) {
                cleanupPlayer(uuid)
                return@forEach
            }
            
            // 이미 태스크가 실행 중이면 스킵
            if (activeTasks.containsKey(uuid)) {
                return@forEach
            }
            
            // 모델이 적용되어 있는지 확인
            if (!EntityModelUtil.hasModel(entity)) {
                return@forEach
            }
            
            val session = EntityModelUtil.getSession(entity) ?: return@forEach
            val model = EntityModelUtil.getModel(entity) ?: return@forEach
            
            // Lower_LeftArm bone 찾기 (대소문자 무시)
            val bone = model.bones.find { 
                it.name.equals("locator", ignoreCase = true)
            } ?: return@forEach
            
            // 파티클 재생 태스크 시작
            startParticleTask(entity, session.mapper, bone.uuid)
        }
        
        // 더 이상 모델이 없는 플레이어의 태스크 정리
        val activeUuids = activeTasks.keys.toSet()
        activeUuids.forEach { uuid ->
            val entity = Bukkit.getEntity(uuid) as? Player
            if (entity == null || !entity.isValid || !EntityModelUtil.hasModel(entity)) {
                cleanupPlayer(uuid)
            }
        }
    }
    
    private fun startParticleTask(player: Player, mapper: BoneDisplayMapper, boneUuid: String) {
        // 이미 태스크가 실행 중이면 중복 생성 방지
        if (activeTasks.containsKey(player.uniqueId)) {
            return
        }
        
        val task = object : BukkitRunnable() {
            override fun run() {
                // 플레이어가 유효하지 않거나 모델이 없으면 태스크 종료
                if (!player.isValid || !EntityModelUtil.hasModel(player)) {
                    cancel()
                    activeTasks.remove(player.uniqueId)
                    return
                }
                
                // Lower_LeftArm bone의 pivot 위치 가져오기
                val pivotLocation = mapper.getBonePivotLocation(boneUuid) ?: return
                
                // 파티클 재생
                player.world.spawnParticle(
                    Particle.FLAME, // 원하는 파티클 타입으로 변경 가능
                    pivotLocation,
                    1, // 파티클 개수
                    0.0, // x 오프셋
                    0.0, // y 오프셋
                    0.0, // z 오프셋
                    0.0 // 속도
                )
            }
        }
        
        // 매 틱마다 실행 (1L = 1 tick = 0.05초)
        val bukkitTask = task.runTaskTimer(CinematicCore.instance, 0L, 1L)
        activeTasks[player.uniqueId] = bukkitTask
    }
    
    private fun cleanupPlayer(uuid: UUID) {
        activeTasks[uuid]?.cancel()
        activeTasks.remove(uuid)
    }
}

