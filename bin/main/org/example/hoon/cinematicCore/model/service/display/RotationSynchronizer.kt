package org.example.hoon.cinematicCore.model.service.display

import org.bukkit.Location
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.lang.Math

class RotationSynchronizer(
    private val plugin: Plugin
) {

    fun start(base: LivingEntity, displays: Collection<ItemDisplay>): BukkitTask {
        return object : BukkitRunnable() {
            private var previousLocation: Location? = null
            private var lastStableYaw: Float = base.bodyYaw
            
            override fun run() {
                if (!base.isValid) {
                    cancel()
                    return
                }

                val currentLocation = base.location
                val prevLoc = previousLocation
                
                // 넉백 여부 감지 (최근 피해로 인한 무적 시간 + 눈에 띄는 속도 변화)
                val isKnockback = base.noDamageTicks > 0 && base.velocity.lengthSquared() > 0.01
                
                // 이동 방향으로 yaw 계산
                var targetYaw = lastStableYaw
                
                if (!isKnockback && prevLoc != null) {
                    val dx = currentLocation.x - prevLoc.x
                    val dz = currentLocation.z - prevLoc.z
                    val distance = Math.sqrt(dx * dx + dz * dz)
                    
                    // 이동 거리가 일정 이상이면 이동 방향으로 yaw 설정
                    if (distance > 0.01) {
                        // 이동 방향 계산 (라디안)
                        val moveYaw = Math.atan2(-dx, dz)
                        // 도 단위로 변환
                        targetYaw = Math.toDegrees(moveYaw).toFloat()
                        lastStableYaw = targetYaw
                    }
                }
                
                previousLocation = currentLocation.clone()

                displays.forEach { display ->
                    if (display.isValid) {
                        display.setRotation(targetYaw, 0.0f)
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }
}

