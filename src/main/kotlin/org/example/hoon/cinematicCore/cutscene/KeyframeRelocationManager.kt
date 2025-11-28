package org.example.hoon.cinematicCore.cutscene

import org.bukkit.entity.Player
import java.util.UUID

/**
 * 키프레임 위치 수정 세션 관리
 */
class KeyframeRelocationManager(
    private val editModeManager: EditModeManager,
    private val keyframeDisplayManager: KeyframeDisplayManager,
    private val pathVisualizer: PathVisualizer,
    private val cutsceneManager: CutsceneManager
) {

    private val sessions: MutableMap<UUID, RelocationSession> = mutableMapOf()
    private var menuOpener: KeyframeMenuOpener? = null

    fun registerMenuOpener(opener: KeyframeMenuOpener) {
        this.menuOpener = opener
    }

    fun startRelocation(player: Player, sceneName: String, keyframe: Keyframe) {
        if (sessions.containsKey(player.uniqueId)) {
            player.sendActionBar("§c이미 다른 키프레임 위치 수정 중입니다.")
            return
        }

        sessions[player.uniqueId] = RelocationSession(
            sceneName = sceneName,
            keyframeIndex = keyframe.index,
            originalKeyframe = keyframe
        )

        keyframeDisplayManager.removeKeyframeDisplay(keyframe.index)
        editModeManager.applyRelocationItems(player)
        player.closeInventory()
        player.sendActionBar("§e새 위치로 이동한 뒤 '위치 확정' 또는 '위치 취소'를 선택하세요.")
    }

    fun confirmRelocation(player: Player): Boolean {
        val session = sessions[player.uniqueId] ?: return false

        val cutscene = cutsceneManager.load(session.sceneName)
        if (cutscene == null) {
            player.sendActionBar("§c컷씬 데이터를 찾을 수 없습니다.")
            abort(player, restoreDisplay = false)
            return false
        }

        val newLocation = player.location.clone()
        val updatedKeyframe = session.originalKeyframe.copy(
            location = newLocation,
            pitch = newLocation.pitch,
            yaw = newLocation.yaw
        )

        val newKeyframes = cutscene.keyframes.map {
            if (it.index == session.keyframeIndex) updatedKeyframe else it
        }

        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)
        if (!cutsceneManager.save(updatedCutscene)) {
            player.sendActionBar("§c키프레임 위치 저장에 실패했습니다.")
            return false
        }

        keyframeDisplayManager.createKeyframeDisplay(updatedKeyframe, player)
        editModeManager.applyDefaultEditItems(player)
        pathVisualizer.refreshPath(player, newKeyframes)
        player.sendActionBar("§a키프레임 #${session.keyframeIndex} 위치가 업데이트되었습니다.")

        sessions.remove(player.uniqueId)
        menuOpener?.openKeyframeMenu(player, session.keyframeIndex)
        return true
    }

    fun cancelRelocation(player: Player): Boolean {
        val session = sessions.remove(player.uniqueId) ?: return false

        keyframeDisplayManager.createKeyframeDisplay(session.originalKeyframe, player)
        editModeManager.applyDefaultEditItems(player)
        player.sendActionBar("§7키프레임 위치 수정을 취소했습니다.")

        menuOpener?.openKeyframeMenu(player, session.keyframeIndex)
        return true
    }

    fun abort(player: Player, restoreDisplay: Boolean = true) {
        val session = sessions.remove(player.uniqueId) ?: return

        if (restoreDisplay) {
            keyframeDisplayManager.createKeyframeDisplay(session.originalKeyframe, player)
        }
        editModeManager.applyDefaultEditItems(player)
    }

    fun isRelocating(player: Player): Boolean {
        return sessions.containsKey(player.uniqueId)
    }

    data class RelocationSession(
        val sceneName: String,
        val keyframeIndex: Int,
        val originalKeyframe: Keyframe
    )

    fun interface KeyframeMenuOpener {
        fun openKeyframeMenu(player: Player, keyframeIndex: Int)
    }
}

