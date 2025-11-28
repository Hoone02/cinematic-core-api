package org.example.hoon.cinematicCore.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.util.prefix
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import kotlin.math.max

/**
 * 서버 성능 모니터링 서비스
 * TPS, MSPT, 메모리 사용량, 엔티티 수, 청크 수 등을 실시간으로 액션바에 표시
 */
class PerformanceMonitorService(private val plugin: CinematicCore) {
    
    private val enabledPlayers = mutableSetOf<Player>()
    private var updateTask: BukkitTask? = null
    
    // TPS 계산을 위한 변수 (더 가벼운 방식)
    private var lastUpdateTime = System.currentTimeMillis()
    private var lastTickCount = 0L
    
    private val decimalFormat = DecimalFormat("#.##")
    private val decimalFormat2 = DecimalFormat("#.#")
    
    /**
     * 플레이어의 성능 모니터링 활성화/비활성화
     */
    fun toggle(player: Player): Boolean {
        val wasEnabled = enabledPlayers.contains(player)
        
        if (wasEnabled) {
            enabledPlayers.remove(player)
            player.sendActionBar("")
            player.sendMessage("§7[§e$prefix§7] 성능 모니터링이 §c비활성화§7되었습니다.")
        } else {
            enabledPlayers.add(player)
            player.sendMessage("§7[§e$prefix§7] 성능 모니터링이 §a활성화§7되었습니다.")
        }
        
        // 첫 번째 플레이어가 활성화하면 업데이트 태스크 시작
        if (enabledPlayers.isNotEmpty() && updateTask == null) {
            startTasks()
        } else if (enabledPlayers.isEmpty() && updateTask != null) {
            stopTasks()
        }
        
        return !wasEnabled
    }
    
    /**
     * 플레이어가 온라인인지 확인하고 오프라인 플레이어 제거
     */
    fun cleanupOfflinePlayers() {
        enabledPlayers.removeIf { !it.isOnline }
        if (enabledPlayers.isEmpty() && updateTask != null) {
            stopTasks()
        }
    }
    
    /**
     * 모든 태스크 시작 (TPS 측정 및 업데이트)
     */
    private fun startTasks() {
        // 초기값 설정
        lastUpdateTime = System.currentTimeMillis()
        lastTickCount = getCurrentTickCount()
        
        updateTask = object : BukkitRunnable() {
            override fun run() {
                if (enabledPlayers.isEmpty()) {
                    cancel()
                    updateTask = null
                    return
                }
                
                // 오프라인 플레이어 정리
                cleanupOfflinePlayers()
                
                // 성능 지표 수집
                val tps = calculateTPS()
                val mspt = getMSPT()
                val memoryInfo = getMemoryInfo()
                val entityCount = getEntityCount()
                val chunkCount = getChunkCount()
                val onlinePlayers = Bukkit.getOnlinePlayers().size
                val cpuUsage = getCPUUsage()
                
                // 액션바 메시지 생성
                val message = buildActionBarMessage(tps, mspt, memoryInfo, entityCount, chunkCount, onlinePlayers, cpuUsage)
                
                // 모든 활성화된 플레이어에게 전송
                enabledPlayers.forEach { player ->
                    if (player.isOnline) {
                        player.sendActionBar(message)
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L) // 1초마다 업데이트 (20틱)
    }
    
    /**
     * 모든 태스크 중지
     */
    private fun stopTasks() {
        updateTask?.cancel()
        updateTask = null
    }
    
    /**
     * 현재 서버 틱 카운트 가져오기 (Paper API 우선)
     */
    private fun getCurrentTickCount(): Long {
        return try {
            // Paper의 ServerTickManager를 통해 틱 카운트 가져오기 시도
            val server = Bukkit.getServer()
            val tickManagerField = server.javaClass.getDeclaredField("tickManager")
            tickManagerField.isAccessible = true
            val tickManager = tickManagerField.get(server)
            val currentTickMethod = tickManager.javaClass.getMethod("currentTick")
            currentTickMethod.invoke(tickManager) as Long
        } catch (e: Exception) {
            // Paper API를 사용할 수 없는 경우 현재 시간 기반으로 추정
            System.currentTimeMillis() / 50 // 대략적인 틱 수 (1틱 = 50ms)
        }
    }
    
    /**
     * TPS 계산
     * Paper API를 우선 사용하고, 실패 시 시간 기반 계산
     */
    private fun calculateTPS(): Double {
        return try {
            // Paper의 ServerTickManager를 통해 TPS 가져오기 시도
            val server = Bukkit.getServer()
            val tickManagerField = server.javaClass.getDeclaredField("tickManager")
            tickManagerField.isAccessible = true
            val tickManager = tickManagerField.get(server)
            
            // TPS 메서드 시도
            try {
                val tpsMethod = tickManager.javaClass.getMethod("tps")
                val tps = tpsMethod.invoke(tickManager) as Double
                return max(0.0, minOf(20.0, tps))
            } catch (e: NoSuchMethodException) {
                // TPS 메서드가 없으면 틱 카운트 기반으로 계산
                val currentTick = getCurrentTickCount()
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastUpdateTime
                
                if (elapsed > 0 && elapsed < 5000) {
                    val ticksElapsed = currentTick - lastTickCount
                    val seconds = elapsed / 1000.0
                    val tps = ticksElapsed / seconds
                    lastUpdateTime = currentTime
                    lastTickCount = currentTick
                    return max(0.0, minOf(20.0, tps))
                }
            }
            
            20.0 // 기본값
        } catch (e: Exception) {
            // Paper API를 사용할 수 없는 경우 시간 기반 계산
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastUpdateTime
            
            if (elapsed > 0 && elapsed < 5000) {
                // 1초에 20틱이므로, 경과 시간에 따라 추정
                val expectedTicks = elapsed / 50.0 // 1틱 = 50ms
                val tps = (expectedTicks / (elapsed / 1000.0)).coerceIn(0.0, 20.0)
                lastUpdateTime = currentTime
                return tps
            }
            
            20.0 // 기본값
        }
    }
    
    /**
     * MSPT 가져오기 (Paper API 우선 시도, 실패 시 백업 방법)
     */
    private fun getMSPT(): Double {
        return try {
            // Paper의 ServerTickManager를 통해 MSPT 가져오기 시도
            val server = Bukkit.getServer()
            
            // 리플렉션을 통해 tickManager 접근 시도
            val tickManagerField = server.javaClass.getDeclaredField("tickManager")
            tickManagerField.isAccessible = true
            val tickManager = tickManagerField.get(server)
            
            // averageTickTime 메서드 호출 시도
            try {
                val averageTickTimeMethod = tickManager.javaClass.getMethod("averageTickTime")
                val averageTickTime = averageTickTimeMethod.invoke(tickManager) as Long
                return averageTickTime / 1_000_000.0 // 나노초를 밀리초로 변환
            } catch (e: NoSuchMethodException) {
                // averageTickTime 메서드가 없으면 TPS 기반으로 추정
                val tps = calculateTPS()
                if (tps > 0) {
                    return 1000.0 / tps // MSPT = 1000ms / TPS
                }
            }
            
            0.0
        } catch (e: Exception) {
            // Paper API를 사용할 수 없는 경우 TPS 기반으로 추정
            val tps = calculateTPS()
            if (tps > 0) {
                return 1000.0 / tps // MSPT = 1000ms / TPS
            }
            0.0
        }
    }
    
    /**
     * 메모리 정보 가져오기
     */
    private fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        
        val usedMB = usedMemory / (1024.0 * 1024.0)
        val maxMB = maxMemory / (1024.0 * 1024.0)
        val usagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100.0
        
        return MemoryInfo(usedMB, maxMB, usagePercent)
    }
    
    /**
     * 엔티티 수 가져오기
     */
    private fun getEntityCount(): Int {
        return try {
            Bukkit.getWorlds().sumOf { it.entityCount }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 청크 수 가져오기
     */
    private fun getChunkCount(): Int {
        return try {
            Bukkit.getWorlds().sumOf { it.loadedChunks.size }
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * CPU 사용률 가져오기
     */
    private fun getCPUUsage(): Double {
        return try {
            val mxBean = ManagementFactory.getOperatingSystemMXBean()
            if (mxBean is com.sun.management.OperatingSystemMXBean) {
                mxBean.processCpuLoad * 100.0
            } else {
                mxBean.systemLoadAverage * 100.0 / mxBean.availableProcessors
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * 액션바 메시지 생성
     */
    private fun buildActionBarMessage(
        tps: Double, 
        mspt: Double, 
        memoryInfo: MemoryInfo, 
        entityCount: Int,
        chunkCount: Int,
        onlinePlayers: Int,
        cpuUsage: Double
    ): String {
        // TPS 색상 결정
        val tpsColor = when {
            tps >= 19.0 -> "§a" // 녹색
            tps >= 15.0 -> "§e" // 노란색
            tps >= 10.0 -> "§6" // 주황색
            else -> "§c" // 빨간색
        }
        
        // MSPT 색상 결정
        val msptColor = when {
            mspt < 20.0 -> "§a" // 녹색 (20ms 미만)
            mspt < 40.0 -> "§e" // 노란색 (20-40ms)
            mspt < 50.0 -> "§6" // 주황색 (40-50ms)
            else -> "§c" // 빨간색 (50ms 이상)
        }
        
        // 메모리 사용률 색상 결정
        val memoryColor = when {
            memoryInfo.usagePercent < 50.0 -> "§a" // 녹색
            memoryInfo.usagePercent < 75.0 -> "§e" // 노란색
            memoryInfo.usagePercent < 90.0 -> "§6" // 주황색
            else -> "§c" // 빨간색
        }
        
        // CPU 사용률 색상 결정
        val cpuColor = when {
            cpuUsage < 50.0 -> "§a" // 녹색
            cpuUsage < 75.0 -> "§e" // 노란색
            cpuUsage < 90.0 -> "§6" // 주황색
            else -> "§c" // 빨간색
        }
        
        return buildString {
            append("§7TPS: ${tpsColor}${decimalFormat.format(tps)}§7 | ")
            append("§7MSPT: ${msptColor}${decimalFormat2.format(mspt)}ms§7 | ")
            append("§7메모리: ${memoryColor}${decimalFormat.format(memoryInfo.usedMB)}§7/§7${decimalFormat.format(memoryInfo.maxMB)}MB ")
            append("${memoryColor}(${decimalFormat.format(memoryInfo.usagePercent)}%)§7 | ")
            append("§7CPU: ${cpuColor}${decimalFormat.format(cpuUsage)}%§7 | ")
            append("§7엔티티: §f$entityCount§7 | ")
            append("§7청크: §f$chunkCount§7 | ")
            append("§7플레이어: §f$onlinePlayers")
        }
    }
    
    /**
     * 서비스 종료
     */
    fun shutdown() {
        stopTasks()
        enabledPlayers.clear()
    }
    
    /**
     * 메모리 정보 데이터 클래스
     */
    private data class MemoryInfo(
        val usedMB: Double,
        val maxMB: Double,
        val usagePercent: Double
    )
}

