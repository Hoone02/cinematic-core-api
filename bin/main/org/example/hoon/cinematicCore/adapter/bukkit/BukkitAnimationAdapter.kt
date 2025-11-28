package org.example.hoon.cinematicCore.adapter.bukkit

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.core.animation.AnimationEngine

/**
 * Bukkit 애니메이션 어댑터
 * AnimationEngine을 Bukkit 스케줄러와 연결
 */
class BukkitAnimationAdapter(
    private val plugin: Plugin,
    private val engine: AnimationEngine,
    private val updateIntervalTicks: Long = 1L
) {
    private var task: BukkitRunnable? = null
    private var isRunning = false
    
    /**
     * 애니메이션 업데이트 시작
     */
    fun start() {
        if (isRunning) {
            stop()
        }
        
        isRunning = true
        val tickSeconds = updateIntervalTicks.coerceAtLeast(1L) * 0.05f
        
        task = object : BukkitRunnable() {
            override fun run() {
                if (!isRunning) {
                    cancel()
                    return
                }
                engine.update(tickSeconds)
            }
        }
        task?.runTaskTimer(plugin, 0L, updateIntervalTicks)
    }
    
    /**
     * 애니메이션 업데이트 정지
     */
    fun stop() {
        isRunning = false
        task?.cancel()
        task = null
    }
    
    /**
     * 실행 중인지 확인
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * 내부 엔진 접근
     */
    fun getEngine(): AnimationEngine = engine
}

