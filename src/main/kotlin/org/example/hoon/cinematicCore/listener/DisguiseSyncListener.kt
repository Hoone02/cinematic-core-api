package org.example.hoon.cinematicCore.listener

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.model.service.ModelDisplaySpawner

/**
 * 플레이어 입장/퇴장 시 모델을 제거하고 다시 적용하는 리스너
 *
 * - 플레이어 퇴장 시: 모델 세션을 완전히 정리하고 Display를 제거하지만, 모델 이름은 유지
 * - 플레이어 입장 시: 저장된 모델 이름이 있으면 자동으로 모델을 다시 적용
 */
class DisguiseSyncListener(
    private val plugin: CinematicCore
) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        // 플레이어에게 적용된 모델 세션이 있는지 확인
        val session = EntityModelUtil.getSession(player)
        if (session != null) {
            val modelName = EntityModelUtil.getModelName(player)
            
            // 모델 세션 완전히 정리 (Display 제거, 애니메이션 정지 등)
            // baseEntity는 플레이어이므로 제거하지 않음
            session.stop(removeBaseEntity = false)
            
            // 모델 이름은 유지하여 재접속 시 다시 적용할 수 있도록 함
            if (modelName != null) {
                EntityModelUtil.rememberModelName(player, modelName)
            }
        } else {
            // 세션이 없더라도 모델 이름이 저장되어 있을 수 있으므로 확인
            val modelName = EntityModelUtil.getModelName(player)
            if (modelName != null) {
                // 모델 이름은 유지
                EntityModelUtil.rememberModelName(player, modelName)
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // 이미 세션이 살아있다면 새로 생성하지 않음 (중복 방지)
        if (EntityModelUtil.getSession(player) != null) {
            return
        }

        // 저장된 모델 이름이 있는지 확인
        val modelName = EntityModelUtil.getModelName(player) ?: return

        // 플레이어가 완전히 로드될 때까지 약간의 지연 후 모델 적용
        object : BukkitRunnable() {
            override fun run() {
                // 플레이어가 오프라인이 되었으면 취소
                if (!player.isOnline) {
                    return
                }

                // 플레이어 투명화 처리 (모델을 보이게 하기 위해)
                player.isInvisible = true

                // 모델 소환
                val spawnResult = ModelDisplaySpawner.spawnByName(
                    plugin = plugin,
                    modelName = modelName,
                    location = player.location,
                    player = player,
                    baseEntity = player,
                    scaledBoneCache = plugin.modelService.scaledBoneCache
                )

                if (spawnResult == null) {
                    // 모델을 찾지 못하면 모델 정보 제거 및 투명화 해제
                    EntityModelUtil.unregister(player)
                    player.removePotionEffect(PotionEffectType.INVISIBILITY)
                    return
                }

                // 애니메이션 컨트롤러 시작
                spawnResult.animationController.start(1L)
            }
        }.runTaskLater(plugin, 1L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        
        // 플레이어에게 적용된 모델 세션이 있는지 확인
        val session = EntityModelUtil.getSession(player)
        if (session == null) {
            // 세션이 없으면 모델이 적용되지 않은 상태
            return
        }
        
        // 모델 이름 저장
        val modelName = EntityModelUtil.getModelName(player) ?: return
        
        // 기존 모델 세션 정리 (Display 제거, 애니메이션 정지 등)
        // baseEntity는 플레이어이므로 제거하지 않음
        session.stop(removeBaseEntity = false)
        
        // 모델 이름은 유지
        EntityModelUtil.rememberModelName(player, modelName)
        
        // 새 월드에서 모델 재적용 (플레이어가 완전히 이동할 때까지 약간의 지연)
        val newWorld = event.player.world
        object : BukkitRunnable() {
            override fun run() {
                // 플레이어가 오프라인이 되었거나 월드가 변경되었으면 취소
                if (!player.isOnline || player.world != newWorld) {
                    return
                }
                
                // 이미 세션이 다시 생성되었다면 중복 방지
                if (EntityModelUtil.getSession(player) != null) {
                    return
                }
                
                // 플레이어 투명화 처리 (모델을 보이게 하기 위해)
                player.isInvisible = true
                
                // 새 위치에서 모델 소환
                val spawnResult = ModelDisplaySpawner.spawnByName(
                    plugin = plugin,
                    modelName = modelName,
                    location = player.location,
                    player = player,
                    baseEntity = player,
                    scaledBoneCache = plugin.modelService.scaledBoneCache
                )
                
                if (spawnResult == null) {
                    // 모델을 찾지 못하면 모델 정보 제거 및 투명화 해제
                    EntityModelUtil.unregister(player)
                    player.removePotionEffect(PotionEffectType.INVISIBILITY)
                    return
                }
                
                // 애니메이션 컨트롤러 시작
                spawnResult.animationController.start(1L)
            }
        }.runTaskLater(plugin, 2L) // 2틱 후 실행 (플레이어가 완전히 이동한 후)
    }
}

