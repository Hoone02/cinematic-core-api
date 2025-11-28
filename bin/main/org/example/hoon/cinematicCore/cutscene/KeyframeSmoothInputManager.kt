package org.example.hoon.cinematicCore.cutscene

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class KeyframeSmoothInputManager(
    private val plugin: Plugin,
    private val cutsceneManager: CutsceneManager,
    private val pathVisualizer: PathVisualizer
) {
    
    fun toggleStartSmooth(player: Player, sceneName: String, keyframeIndex: Int): Boolean {
        val cutscene = cutsceneManager.load(sceneName) ?: return false
        val keyframe = cutscene.keyframes.find { it.index == keyframeIndex } ?: return false
        
        val updatedKeyframe = keyframe.copy(startSmooth = !keyframe.startSmooth)
        val newKeyframes = cutscene.keyframes.map {
            if (it.index == keyframeIndex) updatedKeyframe else it
        }
        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)
        
        return if (cutsceneManager.save(updatedCutscene)) {
            pathVisualizer.refreshPath(player, newKeyframes)
            true
        } else {
            false
        }
    }
    
    fun toggleEndSmooth(player: Player, sceneName: String, keyframeIndex: Int): Boolean {
        val cutscene = cutsceneManager.load(sceneName) ?: return false
        val keyframe = cutscene.keyframes.find { it.index == keyframeIndex } ?: return false
        
        val updatedKeyframe = keyframe.copy(endSmooth = !keyframe.endSmooth)
        val newKeyframes = cutscene.keyframes.map {
            if (it.index == keyframeIndex) updatedKeyframe else it
        }
        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)
        
        return if (cutsceneManager.save(updatedCutscene)) {
            pathVisualizer.refreshPath(player, newKeyframes)
            true
        } else {
            false
        }
    }
}

