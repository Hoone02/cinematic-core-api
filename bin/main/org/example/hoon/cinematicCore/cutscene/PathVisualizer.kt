package org.example.hoon.cinematicCore.cutscene

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * 경로 표시 클래스 (파티클로 키프레임 간 경로 표시)
 */
class PathVisualizer(private val plugin: Plugin) {
    
    // 플레이어별 경로 표시 상태
    private val pathVisible: MutableMap<UUID, Boolean> = mutableMapOf()
    
    // 플레이어별 경로 표시 태스크
    private val pathTasks: MutableMap<UUID, BukkitTask> = mutableMapOf()
    
    /**
     * 경로 표시 토글
     * 
     * @param player 플레이어
     * @param keyframes 키프레임 리스트
     * @return 현재 표시 상태
     */
    fun togglePath(player: Player, keyframes: List<Keyframe>): Boolean {
        val currentState = pathVisible.getOrDefault(player.uniqueId, false)
        val newState = !currentState
        
        if (newState) {
            startPathVisualization(player, keyframes)
        } else {
            stopPathVisualization(player)
        }
        
        pathVisible[player.uniqueId] = newState
        return newState
    }
    
    /**
     * 경로 표시가 이미 켜져 있다면 최신 키프레임으로 즉시 갱신
     */
    fun refreshPath(player: Player, keyframes: List<Keyframe>) {
        if (!pathVisible.getOrDefault(player.uniqueId, false)) {
            return
        }
        startPathVisualization(player, keyframes)
    }
    
    /**
     * 경로 표시 시작
     */
    private fun startPathVisualization(player: Player, keyframes: List<Keyframe>) {
        // 기존 태스크가 있으면 취소
        stopPathVisualization(player)
        
        if (keyframes.isEmpty()) {
            return
        }
        
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stopPathVisualization(player)
                return@Runnable
            }
            
            // 키프레임 위치에 마커 파티클 표시
            keyframes.forEach { keyframe ->
                val loc = keyframe.location.clone()
                player.spawnParticle(
                    Particle.END_ROD,
                    loc,
                    1,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                )
            }
            
            for (i in 0 until keyframes.size - 1) {
                val from = keyframes[i]
                val to = keyframes[i + 1]
                val distance = from.location.distance(to.location)
                val steps = (distance * 2).toInt().coerceAtLeast(5).coerceAtMost(50)

                val p0 = keyframes[maxOf(i - 1, 0)]
                val p3 = keyframes[minOf(i + 2, keyframes.lastIndex)]

                for (step in 0..steps) {
                    val t = step.toDouble() / steps
                    val point = when {
                        from.interpolationType == InterpolationType.BEZIER ->
                            interpolateLocationCatmullRom(p0, from, to, p3, t)
                        else ->
                            interpolateLocationLinear(from.location, to.location, t)
                    }

                    player.spawnParticle(
                        Particle.END_ROD,
                        point,
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    )
                }
            }
        }, 0L, 20L) // 1초마다 업데이트
        
        pathTasks[player.uniqueId] = task
    }
    
    /**
     * 경로 표시 중지
     */
    private fun stopPathVisualization(player: Player) {
        val task = pathTasks.remove(player.uniqueId)
        task?.cancel()
    }
    
    /**
     * 두 위치 간 선형 보간
     */
    private fun interpolateLocationLinear(from: Location, to: Location, t: Double): Location {
        val clampedT = t.coerceIn(0.0, 1.0)
        val x = from.x + (to.x - from.x) * clampedT
        val y = from.y + (to.y - from.y) * clampedT
        val z = from.z + (to.z - from.z) * clampedT
        
        return Location(from.world, x, y, z)
    }
    
    /**
     * Catmull-Rom 스플라인 보간
     */
    private fun interpolateLocationCatmullRom(
        p0: Keyframe,
        p1: Keyframe,
        p2: Keyframe,
        p3: Keyframe,
        t: Double
    ): Location {
        val clampedT = t.coerceIn(0.0, 1.0)

        val t2 = clampedT * clampedT
        val t3 = t2 * clampedT

        fun catmullComponent(c0: Double, c1: Double, c2: Double, c3: Double): Double {
            return 0.5 * (
                (2 * c1) +
                    (-c0 + c2) * clampedT +
                    (2 * c0 - 5 * c1 + 4 * c2 - c3) * t2 +
                    (-c0 + 3 * c1 - 3 * c2 + c3) * t3
                )
        }

        val x = catmullComponent(p0.location.x, p1.location.x, p2.location.x, p3.location.x)
        val y = catmullComponent(p0.location.y, p1.location.y, p2.location.y, p3.location.y)
        val z = catmullComponent(p0.location.z, p1.location.z, p2.location.z, p3.location.z)

        return Location(p1.location.world, x, y, z)
    }
    
    /**
     * 플레이어의 경로 표시 상태 확인
     */
    fun isPathVisible(player: Player): Boolean {
        return pathVisible.getOrDefault(player.uniqueId, false)
    }
    
    /**
     * 플레이어 정리 (오프라인 시)
     */
    fun cleanup(player: Player) {
        stopPathVisualization(player)
        pathVisible.remove(player.uniqueId)
    }

}

