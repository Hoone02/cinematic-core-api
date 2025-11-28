package org.example.hoon.cinematicCore.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.cutscene.CutsceneManager
import org.example.hoon.cinematicCore.cutscene.EditModeManager
import org.example.hoon.cinematicCore.cutscene.KeyframeDisplayManager
import org.example.hoon.cinematicCore.cutscene.PathVisualizer
import org.example.hoon.cinematicCore.cutscene.CutscenePlayer

/**
 * 컷씬 관련 명령어 처리 클래스
 */
class CutsceneCommand(
    private val cutsceneManager: CutsceneManager,
    private val editModeManager: EditModeManager,
    private val keyframeDisplayManager: KeyframeDisplayManager,
    private val pathVisualizer: PathVisualizer,
    private val cutscenePlayer: CutscenePlayer
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§c사용법: /${label} <create|edit|play|stop|list|delete> [씬이름]")
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                
                if (args.size < 2) {
                    sender.sendMessage("§c사용법: /${label} create <씬이름>")
                    return true
                }
                
                val sceneName = args[1]
                val player = sender as Player
                
                if (cutsceneManager.exists(sceneName)) {
                    sender.sendMessage("§c이미 존재하는 컷씬입니다: $sceneName")
                    return true
                }
                
                // 컷씬 생성
                val cutscene = org.example.hoon.cinematicCore.cutscene.Cutscene(sceneName, emptyList())
                if (!cutsceneManager.save(cutscene)) {
                    sender.sendMessage("§c컷씬 생성 실패")
                    return true
                }
                
                // 수정모드 진입
                if (editModeManager.enterEditMode(player, sceneName)) {
                    sender.sendMessage("§a컷씬 생성: §f$sceneName §7(수정모드 진입)")
                } else {
                    sender.sendMessage("§c수정모드 진입 실패")
                }
            }
            
            "edit" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                
                if (args.size < 2) {
                    sender.sendMessage("§c사용법: /${label} edit <씬이름>")
                    return true
                }
                
                val sceneName = args[1]
                val player = sender as Player
                
                if (!cutsceneManager.exists(sceneName)) {
                    sender.sendMessage("§c컷씬을 찾을 수 없습니다: $sceneName")
                    return true
                }
                
                // 수정모드 진입
                if (editModeManager.enterEditMode(player, sceneName)) {
                    // 키프레임 표시
                    val cutscene = cutsceneManager.load(sceneName)
                    if (cutscene != null && cutscene.keyframes.isNotEmpty()) {
                        keyframeDisplayManager.createAllKeyframeDisplays(cutscene.keyframes, player)
                    }
                    player.sendActionBar("§a수정모드 진입: §f$sceneName")
                } else {
                    player.sendActionBar("§c수정모드 진입 실패")
                }
            }
            
            "play" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                
                if (args.size < 2) {
                    sender.sendMessage("§c사용법: /${label} play <씬이름>")
                    return true
                }
                
                val sceneName = args[1]
                val player = sender as Player
                
                val cutscene = cutsceneManager.load(sceneName)
                
                if (cutscene == null) {
                    player.sendActionBar("§c컷씬을 찾을 수 없습니다: $sceneName")
                    return true
                }
                
                if (cutscene.keyframes.isEmpty()) {
                    player.sendActionBar("§c키프레임이 없습니다.")
                    return true
                }
                
                if (cutscenePlayer.play(cutscene, player)) {
                    player.sendActionBar("§a컷씬 재생 시작: §f$sceneName")
                } else {
                    player.sendActionBar("§c컷씬 재생 실패")
                }
            }
            
            "stop" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                
                val player = sender as Player
                
                if (!cutscenePlayer.playingPlayers.containsKey(player)) {
                    player.sendActionBar("§c재생 중인 컷씬이 없습니다.")
                    return true
                }
                
                cutscenePlayer.stop(player)
                player.sendActionBar("§a컷씬 재생이 중지되었습니다.")
            }
            
            "list" -> {
                val scenes = cutsceneManager.list()
                if (scenes.isEmpty()) {
                    sender.sendMessage("§7저장된 컷씬이 없습니다.")
                } else {
                    sender.sendMessage("§a저장된 컷씬 (${scenes.size}개):")
                    scenes.forEach { sceneName ->
                        sender.sendMessage("§7- §f$sceneName")
                    }
                }
            }
            
            "delete" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c사용법: /${label} delete <씬이름>")
                    return true
                }
                
                val sceneName = args[1]
                if (!cutsceneManager.exists(sceneName)) {
                    sender.sendMessage("§c컷씬을 찾을 수 없습니다: $sceneName")
                    return true
                }
                
                if (cutsceneManager.delete(sceneName)) {
                    sender.sendMessage("§a컷씬 삭제 완료: §f$sceneName")
                } else {
                    sender.sendMessage("§c컷씬 삭제 실패: §f$sceneName")
                }
            }
            
            else -> {
                sender.sendMessage("§c알 수 없는 명령어입니다. 사용법: /${label} <create|edit|play|stop|list|delete> [씬이름]")
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val completions = mutableListOf<String>()
        
        if (args.size == 1) {
            val subCommands = listOf("create", "edit", "play", "stop", "list", "delete")
            val input = args[0].lowercase()
            
            subCommands.forEach { subCommand ->
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand)
                }
            }
        } else if (args.size == 2 && (args[0].lowercase() == "edit" || args[0].lowercase() == "play" || args[0].lowercase() == "delete")) {
            val scenes = cutsceneManager.list()
            val input = args[1].lowercase()
            
            scenes.forEach { sceneName ->
                if (sceneName.lowercase().startsWith(input)) {
                    completions.add(sceneName)
                }
            }
        }
        
        return completions
    }
}

