package org.example.hoon.cinematicCore.util.animation

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player

/**
 * 모든 AnimationController를 통합 관리하는 태스크 매니저
 * 단일 태스크로 모든 컨트롤러를 업데이트하여 성능 최적화
 */
class AnimationTaskManager(private val plugin: Plugin) {
    private val controllers = mutableSetOf<AnimationController>()
    private var task: BukkitRunnable? = null
    private var isRunning = false

    /**
     * AnimationController를 등록하고 태스크가 실행 중이 아니면 시작
     */
    fun register(controller: AnimationController) {
        controllers.add(controller)
        
        if (!isRunning) {
            start()
        }
    }

    /**
     * AnimationController를 해제하고 컨트롤러가 없으면 태스크 중지
     */
    fun unregister(controller: AnimationController) {
        controllers.remove(controller)
        
        if (controllers.isEmpty() && isRunning) {
            stop()
        }
    }

    /**
     * 통합 태스크 시작
     */
    private fun start() {
        if (isRunning) {
            return
        }

        isRunning = true
        task = object : BukkitRunnable() {
            override fun run() {
                if (!isRunning || controllers.isEmpty()) {
                    stop()
                    return
                }

                // 모든 컨트롤러 업데이트 (배치 처리로 GC 부하 감소)
                // 유효하지 않은 컨트롤러는 한 번에 제거
                val invalidControllers = mutableListOf<AnimationController>()
                val iterator = controllers.iterator()
                
                while (iterator.hasNext()) {
                    val controller = iterator.next()
                    
                    // 컨트롤러가 유효하지 않으면 나중에 제거
                    if (!controller.isValid()) {
                        invalidControllers.add(controller)
                        continue
                    }
                    
                    // 업데이트 필요 여부 확인 후 업데이트 (불필요한 업데이트 스킵)
                    if (controller.needsUpdate()) {
                        try {
                            controller.updateFromTask()
                        } catch (e: Exception) {
                            // 예외 발생 시 나중에 제거
                            invalidControllers.add(controller)
                        }
                    }
                }
                
                // 유효하지 않은 컨트롤러 일괄 제거 (GC 부하 감소)
                invalidControllers.forEach { controllers.remove(it) }
            }
        }
        task?.runTaskTimer(plugin, 0L, 1L) // 매 틱 실행
    }

    /**
     * 통합 태스크 중지
     */
    private fun stop() {
        isRunning = false
        task?.cancel()
        task = null
    }

    /**
     * 모든 컨트롤러 해제 및 태스크 중지
     */
    fun shutdown() {
        controllers.clear()
        stop()
    }

    /**
     * 현재 등록된 컨트롤러 수
     */
    fun getControllerCount(): Int = controllers.size

    companion object {
        @Volatile
        private var instance: AnimationTaskManager? = null

        /**
         * 싱글톤 인스턴스 가져오기
         */
        fun getInstance(plugin: Plugin): AnimationTaskManager {
            return instance ?: synchronized(this) {
                instance ?: AnimationTaskManager(plugin).also { instance = it }
            }
        }
    }
}

