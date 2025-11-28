package org.example.hoon.cinematicCore.cutscene

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.Plugin
import java.util.UUID

class KeyframePauseInputManager(
    private val plugin: Plugin,
    private val cutsceneManager: CutsceneManager,
    private val pathVisualizer: PathVisualizer
) : Listener {

    private val pendingSessions: MutableMap<UUID, PauseSession> = mutableMapOf()
    private var menuOpener: KeyframeMenuOpener? = null

    fun registerMenuOpener(opener: KeyframeMenuOpener) {
        this.menuOpener = opener
    }

    fun requestPauseInput(player: Player, sceneName: String, keyframeIndex: Int) {
        pendingSessions[player.uniqueId] = PauseSession(sceneName, keyframeIndex)
        player.sendActionBar("§e키프레임 #$keyframeIndex 멈춤 시간 입력: 초 단위 입력 (cancel/0으로 제거)")
    }

    fun abort(player: Player) {
        pendingSessions.remove(player.uniqueId)
    }

    @EventHandler
    fun onAsyncPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val session = pendingSessions[player.uniqueId] ?: return
        event.isCancelled = true

        val message = event.message.trim()
        plugin.server.scheduler.runTask(plugin, Runnable {
            when {
                message.equals("cancel", true) || message.equals("취소", true) -> {
                    pendingSessions.remove(player.uniqueId)
                    player.sendActionBar("§7멈춤 시간 설정을 취소했습니다.")
                    menuOpener?.openKeyframeMenu(player, session.keyframeIndex)
                }
                message == "0" -> {
                    if (updateKeyframePause(player, session, null)) {
                        player.sendActionBar("§a키프레임 #${session.keyframeIndex} 멈춤 시간을 제거했습니다.")
                        pendingSessions.remove(player.uniqueId)
                        menuOpener?.openKeyframeMenu(player, session.keyframeIndex)
                    }
                }
                else -> {
                    val seconds = message.toDoubleOrNull()
                    if (seconds == null || seconds < 0.0) {
                        player.sendActionBar("§c0 이상의 값을 입력하거나 'cancel'로 취소하세요.")
                        return@Runnable
                    }
                    if (updateKeyframePause(player, session, seconds)) {
                        player.sendActionBar("§a키프레임 #${session.keyframeIndex} 멈춤 시간을 ${"%.2f".format(seconds)}초로 설정했습니다.")
                        pendingSessions.remove(player.uniqueId)
                        menuOpener?.openKeyframeMenu(player, session.keyframeIndex)
                    }
                }
            }
        })
    }

    private fun updateKeyframePause(player: Player, session: PauseSession, seconds: Double?): Boolean {
        val cutscene = cutsceneManager.load(session.sceneName)
        if (cutscene == null) {
            player.sendActionBar("§c컷씬을 찾을 수 없습니다: ${session.sceneName}")
            pendingSessions.remove(player.uniqueId)
            return false
        }

        val keyframe = cutscene.keyframes.find { it.index == session.keyframeIndex }
        if (keyframe == null) {
            player.sendActionBar("§c키프레임을 찾을 수 없습니다: #${session.keyframeIndex}")
            pendingSessions.remove(player.uniqueId)
            return false
        }

        val updatedKeyframe = keyframe.copy(pauseSeconds = seconds)
        val newKeyframes = cutscene.keyframes.map {
            if (it.index == session.keyframeIndex) updatedKeyframe else it
        }
        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)

        return if (cutsceneManager.save(updatedCutscene)) {
            pathVisualizer.refreshPath(player, newKeyframes)
            true
        } else {
            player.sendActionBar("§c멈춤 시간 저장에 실패했습니다.")
            false
        }
    }

    data class PauseSession(
        val sceneName: String,
        val keyframeIndex: Int
    )

    fun interface KeyframeMenuOpener {
        fun openKeyframeMenu(player: Player, keyframeIndex: Int)
    }
}

