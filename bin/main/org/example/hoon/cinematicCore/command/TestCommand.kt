package org.example.hoon.cinematicCore.command

import io.papermc.paper.configuration.type.Duration
import net.minecraft.util.datafix.fixes.EffectDurationFix
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.EnderMan
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftHusk
import org.bukkit.entity.Enderman
import org.bukkit.entity.Husk
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.model.domain.getBonesByTag
import org.example.hoon.cinematicCore.model.service.ModelDisplaySpawner
import org.example.hoon.cinematicCore.model.service.display.DisplaySession
import kotlin.math.PI

class TestCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }
        val player = sender

        if (args.isEmpty()) {
            player.sendMessage("§e사용법: /$label <모델명> [애니메이션명] [재생속도]")
            player.sendMessage("§7재생속도 기본값은 1.0 (Blockbench 원본 속도)")
            return true
        }

        val modelName = args[0]
        val requestedAnimationName = args.getOrNull(1)
        val requestedSpeed = args.getOrNull(2)?.toFloatOrNull()


//        val husk = player.world.spawn(player.location, Husk::class.java)
//        husk.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 99999, 1))

        val spawnResult = ModelDisplaySpawner.spawnByName(
            plugin = CinematicCore.instance,
            modelName = modelName,
            location = player.location,
            player = player,
            scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache,
        )


        if (spawnResult == null) {
            player.sendMessage("§c모델을 불러오지 못했습니다: $modelName")
        return true
        }

        val modelLoader = CinematicCore.instance.modelService.getModelFileLoader()
        val replacementModel = modelLoader.loadModelByName("player_spear")

        if (replacementModel == null) {
            player.sendMessage("§cplayer_battle 모델을 불러오지 못했습니다.")
            return true
        }

        val boneReplaced = spawnResult.session.replaceBoneWithModel(
            targetBoneName = "group",
            replacementModel = replacementModel,
            replacementBoneName = "group"
        )

        if (!boneReplaced) {
            player.sendMessage("§cUpper_LeftArm 본 교체에 실패했습니다.")
        } else {
            player.sendMessage("§aUpper_LeftArm 본을 player_battle 모델의 동일한 본으로 교체했습니다.")
        }

        val animationController = spawnResult.animationController
        // animationController.start(1L)은 ModelDisplaySpawner.spawn에서 이미 호출됨
        val animations = spawnResult.model.animations
        if (animations.isEmpty()) {
            player.sendMessage("§e${spawnResult.model.modelName} 모델에는 애니메이션 데이터가 없습니다.")
            return true
        }

        val animation = if (requestedAnimationName != null) {
            animations.find { it.name.equals(requestedAnimationName, ignoreCase = true) }
        } else {
            animations.first()
        }

        if (animation == null) {
            player.sendMessage("§c애니메이션을 찾지 못했습니다: $requestedAnimationName")
            player.sendMessage("§7사용 가능: ${animations.joinToString { it.name }}")
            return true
        }

        val playbackSpeed = requestedSpeed?.takeIf { it > 0f } ?: 1f
        animationController.playAnimation(animation, playbackSpeed)

        player.sendMessage("§a${spawnResult.model.modelName} 모델 소환 완료")
        player.sendMessage("§a애니메이션 재생: §f${animation.name} §7(루프: ${animation.loopMode}, 속도: ${"%.2f".format(playbackSpeed)})")
        if (animations.size > 1) {
            player.sendMessage("§7다른 애니메이션: ${animations.filter { it != animation }.joinToString { it.name }}")
        }

        return true
    }
}

