package org.example.hoon.cinematicCore.cutscene

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

/**
 * 플레이어 오프라인 시 컷씬 관련 데이터 정리
 */
class CutsceneCleanupListener(
    private val editModeManager: EditModeManager,
    private val keyframeDisplayManager: KeyframeDisplayManager,
    private val pathVisualizer: PathVisualizer,
    private val relocationManager: KeyframeRelocationManager,
    private val timingInputManager: KeyframeTimingInputManager,
    private val pauseInputManager: KeyframePauseInputManager
) : Listener {
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        // 수정모드에 있으면 종료
        if (editModeManager.isInEditMode(player)) {
            relocationManager.abort(player, restoreDisplay = false)
            timingInputManager.abort(player)
            pauseInputManager.abort(player)
            editModeManager.exitEditMode(player)
        }
        
        // 키프레임 표시 제거
        keyframeDisplayManager.removeAllKeyframeDisplays(player)
        
        // 경로 표시 중지
        pathVisualizer.cleanup(player)
        
        // 정리
        editModeManager.cleanup(player)
    }
}

