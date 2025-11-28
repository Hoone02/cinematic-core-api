package org.example.hoon.cinematicCore.command

import org.bukkit.*
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.api.CinematicCoreAPI
import org.example.hoon.cinematicCore.api.animation.AnimationEventAPI
import org.example.hoon.cinematicCore.model.domain.getBone
import org.example.hoon.cinematicCore.model.service.EntityModelUtil
import org.example.hoon.cinematicCore.model.service.ModelDisplaySpawner
import org.example.hoon.cinematicCore.service.ReloadService
import org.example.hoon.cinematicCore.util.animation.BoneDisplayMapper
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventContext
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventSignalManager
import org.example.hoon.cinematicCore.util.block.CircularConfig
import org.example.hoon.cinematicCore.util.block.DetectionType
import org.example.hoon.cinematicCore.util.block.Earthquake
import org.example.hoon.cinematicCore.util.block.EarthquakeType
import org.example.hoon.cinematicCore.util.block.ForwardRectangleConfig
import org.example.hoon.cinematicCore.util.display.updateTransformation
import java.util.*
import kotlin.math.cos
import kotlin.math.sin


/**
 * CinematicCore 메인 명령어 클래스
 * 명령어 파싱 및 라우팅만 담당
 */
class Command(
    private val reloadService: ReloadService,
    private val cutsceneCommand: CutsceneCommand? = null
) : CommandExecutor, TabCompleter {
    companion object {
        private val debugEventWatchers: MutableMap<UUID, MutableSet<String>> = mutableMapOf()
    }
    
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§c사용법: /${label} <reload|summon|disguise|undisguise|spawntest|testspawn|scene>")
            return true
        }
        when (args[0].lowercase()) {
            "scene" -> {
                // scene 서브커맨드는 CutsceneCommand로 위임
                val commandToUse = cutsceneCommand ?: run {
                    // fallback: 인스턴스가 없으면 새로 생성 (하위 호환성)
                    val cutsceneManager = org.example.hoon.cinematicCore.cutscene.CutsceneManager(CinematicCore.instance)
                    val editModeManager = org.example.hoon.cinematicCore.cutscene.EditModeManager(CinematicCore.instance)
                    val keyframeDisplayManager = org.example.hoon.cinematicCore.cutscene.KeyframeDisplayManager(CinematicCore.instance)
                    val pathVisualizer = org.example.hoon.cinematicCore.cutscene.PathVisualizer(CinematicCore.instance)
                    val cutscenePlayer = org.example.hoon.cinematicCore.cutscene.CutscenePlayer(CinematicCore.instance)
                    CutsceneCommand(
                        cutsceneManager,
                        editModeManager,
                        keyframeDisplayManager,
                        pathVisualizer,
                        cutscenePlayer
                    )
                }
                val subArgs = args.drop(1).toTypedArray()
                return commandToUse.onCommand(sender, command, label, subArgs)
            }
            "reload" -> {
                reloadService.reload(sender)
            }
            "summon" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                if (args.size < 2) {
                    player.sendMessage("§c사용법: /${label} summon <모델명>")
                    return true
                }
                
                val modelName = args[1]
                val pig = player.world.spawn(player.location, Pig::class.java)
                pig.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, -1, 1, false, false))

                val spawnResult = ModelDisplaySpawner.spawnByName(
                    plugin = CinematicCore.instance,
                    modelName = modelName,
                    location = player.location,
                    player = player,
                    scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache,
                    baseEntity = pig
                )
                
                if (spawnResult == null) {
                    pig.remove()
                    player.sendMessage("§c모델을 찾지 못했습니다: $modelName")
                    player.sendMessage("§7사용 가능한 모델: ${CinematicCore.instance.modelFilesystem.existingModelNames().joinToString()}")
                    return true
                }
                
                // 애니메이션 컨트롤러는 ModelDisplaySpawner.spawn에서 자동으로 시작됨
                val animations = spawnResult.model.animations
                if (animations.isNotEmpty()) {
                    val animation = animations.first()
                    player.sendMessage("§a모델 소환 완료: §f${spawnResult.model.modelName}")
                    player.sendMessage("§a애니메이션 재생: §f${animation.name} §7(루프: ${animation.loopMode}, 속도: 1.00)")
                    if (animations.size > 1) {
                        player.sendMessage("§7다른 애니메이션: ${animations.filter { it != animation }.joinToString { it.name }}")
                    }
                } else {
                    player.sendMessage("§a모델 소환 완료: §f${spawnResult.model.modelName}")
                    player.sendMessage("§e이 모델에는 애니메이션 데이터가 없습니다.")
                }
            }
            "disguise" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                if (args.size < 2) {
                    player.sendMessage("§c사용법: /${label} disguise <모델명>")
                    return true
                }
                
                // 이미 모델이 적용되어 있으면 먼저 해제
                if (EntityModelUtil.hasModel(player)) {
                    val session = EntityModelUtil.getSession(player)
                    session?.stop(removeBaseEntity = false) // 플레이어는 제거하지 않음
                    player.sendMessage("§7기존 모델 해제 중...")
                }

//                player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, -1, 1, true, false))
                player.isInvisible = true
                
                val modelName = args[1]
                val spawnResult = ModelDisplaySpawner.spawnByName(
                    plugin = CinematicCore.instance,
                    modelName = modelName,
                    location = player.location,
                    player = player,
                    baseEntity = player, // 플레이어를 baseEntity로 사용
                    scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache
                )

                
                if (spawnResult == null) {
                    player.sendMessage("§c모델을 찾지 못했습니다: $modelName")
                    player.sendMessage("§7사용 가능한 모델: ${CinematicCore.instance.modelFilesystem.existingModelNames().joinToString()}")
                    return true
                }
                val animationController = spawnResult.animationController
                animationController.start(1L)
                
                val animations = spawnResult.model.animations
                if (animations.isNotEmpty()) {
                    val animation = animations.first()
                    player.sendMessage("§a모델 적용 완료: §f${spawnResult.model.modelName}")
                    player.sendMessage("§a애니메이션 재생: §f${animation.name} §7(루프: ${animation.loopMode}, 속도: 1.00)")
                    if (animations.size > 1) {
                        player.sendMessage("§7다른 애니메이션: ${animations.filter { it != animation }.joinToString { it.name }}")
                    }
                } else {
                    player.sendMessage("§a모델 적용 완료: §f${spawnResult.model.modelName}")
                    player.sendMessage("§e이 모델에는 애니메이션 데이터가 없습니다.")
                }
            }
            "undisguise" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                if (!EntityModelUtil.hasModel(player)) {
                    player.sendMessage("§c적용된 모델이 없습니다.")
                    return true
                }
                
                val session = EntityModelUtil.getSession(player)
                if (session == null) {
                    player.sendMessage("§c모델 세션을 찾을 수 없습니다.")
                    EntityModelUtil.unregister(player)
                    return true
                }
                
                val modelName = EntityModelUtil.getModelName(player)
                player.isInvisible = false
                session.stop(removeBaseEntity = false) // 플레이어는 제거하지 않음
                player.sendMessage("§a모델 해제 완료: §f${modelName ?: "알 수 없음"}")
            }
            "test" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                // 현재 플레이어에게 disguise되어 있는 모델 확인
                if (!EntityModelUtil.hasModel(player)) {
                    player.sendMessage("§c적용된 모델이 없습니다. 먼저 /${label} disguise <모델명>을 사용하세요.")
                    return true
                }
                
                val session = EntityModelUtil.getSession(player)
                if (session == null) {
                    player.sendMessage("§c모델 세션을 찾을 수 없습니다.")
                    EntityModelUtil.unregister(player)
                    return true
                }
                
                // player_battle 모델 로드
                val modelLoader = CinematicCore.instance.modelService.getModelFileLoader()
                val replacementModel = modelLoader.loadModelByName("player_battle")
                
                if (replacementModel == null) {
                    player.sendMessage("§c모델을 찾지 못했습니다: player_battle")
                    return true
                }
                
                // righthandle bone을 player_battle 모델의 group bone으로 교체
                val boneReplaced = session.replaceBoneWithModel(
                    targetBoneName = "righthandle",
                    replacementModel = replacementModel,
                    replacementBoneName = "group"
                )
                
                if (boneReplaced) {
                    player.sendMessage("§aBone 교체 완료: righthandle → player_battle/group")
                } else {
                    player.sendMessage("§cBone 교체 실패: righthandle 또는 player_battle/group을 찾을 수 없습니다.")
                }
            }
            "test2" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                // 오프셋 설정 (forward, up, right) 또는 boneUuid
                val arg1 = args.getOrNull(1)
                val arg2 = args.getOrNull(2)
                val arg3 = args.getOrNull(3)
                val arg4 = args.getOrNull(4)
                
                // 첫 번째 인자가 bone 이름인지 확인 (문자열이면 bone 이름으로 간주)
                val boneName = if (arg1 != null && arg1.toDoubleOrNull() == null) {
                    arg1 // bone 이름
                } else {
                    null
                }
                
                val forward = if (boneName == null) arg1?.toDoubleOrNull() ?: 0.0 else 0.0
                val up = if (boneName == null) arg2?.toDoubleOrNull() ?: 0.0 else arg2?.toDoubleOrNull() ?: 0.0
                val right = if (boneName == null) arg3?.toDoubleOrNull() ?: 0.0 else arg3?.toDoubleOrNull() ?: 0.0
                val rotateWithVehicle = if (boneName == null) arg4?.toBooleanStrictOrNull() ?: false else arg3?.toBooleanStrictOrNull() ?: false
                
                // 돼지 스폰
                val pig = player.world.spawn(player.location, Pig::class.java)
                val spawnResult = ModelDisplaySpawner.spawnByName(
                    plugin = CinematicCore.instance,
                    modelName = "chungho",
                    location = player.location,
                    player = player,
                    baseEntity = pig,
                    scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache
                )
                
                if (spawnResult == null) {
                    player.sendMessage("§c모델을 불러오지 못했습니다: chungho")
                    return true
                }
                
                // boneUuid 찾기 (bone 이름이 지정된 경우)
                val boneUuid = if (boneName != null) {
                    val bone = spawnResult.model.getBone(boneName.lowercase())
                    if (bone != null) {
                        bone.uuid
                    } else {
                        player.sendMessage("§cBone을 찾을 수 없습니다: $boneName")
                        player.sendMessage("§7사용 가능한 bone: ${spawnResult.model.bones.joinToString { it.name }}")
                        return true
                    }
                } else {
                    null
                }
                
                try {
                    // API를 사용하여 커스텀 오프셋으로 탑승
                    val task = CinematicCoreAPI.Bone.mountWithOffset(
                        vehicle = spawnResult.baseEntity,
                        passenger = player,
                        forward = forward,
                        up = up,
                        right = right,
                        rotateWithVehicle = rotateWithVehicle,
                        boneUuid = boneUuid,
                        canDismount = false  // 내릴 수 없음
                    )
                    
                    if (task != null) {
                        if (boneUuid != null) {
                            player.sendMessage("§a커스텀 모델에 탑승했습니다.")
                            player.sendMessage("§7탑승 위치: bone '$boneName'의 pivot 위치")
                        } else {
                            player.sendMessage("§a커스텀 모델에 탑승했습니다.")
                            player.sendMessage("§7오프셋: 전방=$forward, 상하=$up, 좌우=$right")
                        }
                        player.sendMessage("§7사용법: /${label} test2 [bone이름] 또는 /${label} test2 [전방] [상하] [좌우]")
                    } else {
                        player.sendMessage("§c탑승 실패")
                    }
                } catch (e: Exception) {
                    player.sendMessage("§c오류 발생: ${e.message}")
                    e.printStackTrace()
                }
            }
            "testspawn" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                if (args.size < 4) {
                    player.sendMessage("§c사용법: /${label} testspawn <모델명> <마릿수> <범위>")
                    return true
                }
                
                val modelName = args[1]
                val count = args[2].toIntOrNull()
                val range = args[3].toDoubleOrNull()
                
                if (count == null || count <= 0) {
                    player.sendMessage("§c마릿수는 양수여야 합니다.")
                    return true
                }
                
                if (range == null || range <= 0) {
                    player.sendMessage("§c범위는 양수여야 합니다.")
                    return true
                }
                
                val baseLocation = player.location
                val world = player.world
                val random = Random()
                var successCount = 0
                var failCount = 0
                
                player.sendMessage("§7모델 소환 시작: §f$modelName §7($count 마리, 범위: ${range}블록)")
                
                for (i in 0 until count) {
                    // 범위 내 랜덤 좌표 생성
                    val offsetX = (random.nextDouble() - 0.5) * 2 * range
                    val offsetZ = (random.nextDouble() - 0.5) * 2 * range
                    val spawnLocation = baseLocation.clone().add(offsetX, 0.0, offsetZ)
                    
                    // 청크가 비활성화되어 있으면 활성화
                    val chunk = world.getChunkAt(spawnLocation)
                    if (!chunk.isLoaded) {
                        chunk.load()
                    }
                    
                    // 돼지 소환 및 persistent 설정
                    val pig = world.spawn(spawnLocation, Pig::class.java) { entity ->
                        entity.setPersistent(true) // 플레이어와 멀어져도 사라지지 않도록
                    }
                    pig.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, -1, 1))
                    
                    val spawnResult = ModelDisplaySpawner.spawnByName(
                        plugin = CinematicCore.instance,
                        modelName = modelName,
                        location = spawnLocation,
                        player = player,
                        scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache,
                        baseEntity = pig
                    )
                    
                    if (spawnResult == null) {
                        pig.remove()
                        failCount++
                    } else {
                        // Display 엔티티들도 persistent로 설정
                        val session = spawnResult.session
                        session.mapper.getAllDisplays().forEach { display ->
                            display.setPersistent(true) // 플레이어와 멀어져도 사라지지 않도록
                        }
                        successCount++
                    }
                    
                    // 진행 상황 표시 (10마리마다)
                    if ((i + 1) % 10 == 0) {
                        player.sendMessage("§7진행 중... §f${i + 1}/$count")
                    }
                }
                
                player.sendMessage("§a소환 완료: §f${successCount}마리 성공, §c${failCount}마리 실패")
                if (failCount > 0) {
                    player.sendMessage("§7모델 '$modelName'을 찾지 못한 엔티티가 있습니다.")
                }
            }
            "test4" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender as Player
                
                if (args.size < 3) {
                    player.sendMessage("§c사용법: /${label} test4 <반지름> <복원시간(초)>")
                    return true
                }
                
                val radius = args[1].toIntOrNull()
                if (radius == null || radius <= 0) {
                    player.sendMessage("§c반지름은 양의 정수여야 합니다.")
                    return true
                }
                
                val restoreTime = args[2].toLongOrNull()
                if (restoreTime == null || restoreTime <= 0) {
                    player.sendMessage("§c복원 시간은 양의 정수여야 합니다.")
                    return true
                }
                
                // 라이브러리 클래스 사용
                val earthquake = Earthquake(CinematicCore.instance)
                earthquake.execute(
                    centerLocation = player.location.block.location,
                    player = player,
                    type = DetectionType.CIRCULAR,
                    config = CircularConfig(radius),
                    restoreTimeSeconds = restoreTime,
                    sequential = true,
                    startFromCenter = true,
                    totalSequentialTime = 0.4f,
                    randomSize = true
                )
            }
            "test5" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender

                val success = CinematicCoreAPI.Animation.playAnimationByName(
                    entity = player,
                    animationName = "skill",
                    speed = 1.0f,
                    interruptible = true
                )
                val modelInfo = CinematicCoreAPI.Model.getModelInfo(player)
                val model = modelInfo!!.model
                val session = modelInfo.session
                val bone = model!!.bones.find {
                    it.name.equals("locator", ignoreCase = true)
                }

                val receiver = AnimationEventAPI.register(player)

                receiver.on("flameon") { ctx ->
                    val pivotLocation = session.mapper.getBonePivotLocation(bone!!.uuid)
                    player.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,  // 파티클 타입
                        pivotLocation,                        // 위치
                        20,                         // 파티클 개수
                        0.3,                        // X축 퍼짐
                        0.3,                        // Y축 퍼짐
                        0.3,                        // Z축 퍼짐
                        0.01                        // 속도/애니메이션 속도
                    );
                    startParticleTask(player, session.mapper, bone.uuid)
                    player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 0.7f, 0.7f)
                    player.playSound(player.location, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.5f)
                }
                receiver.on("fly") { ctx ->
                    player.playSound(player.location, Sound.ENTITY_GHAST_SHOOT, 0.3f, 1f)
                }
                receiver.on("jump") { ctx ->
                    player.velocity = player.location.direction.multiply(0.5)
                }
                receiver.on("fire") { ctx ->
                    val pivotLocation = session.mapper.getBonePivotLocation(bone!!.uuid)
                    player.playSound(player.location, Sound.ENTITY_WITHER_HURT, 0.7f, 0.2f)
                    player.playSound(player.location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.7f, 0.2f)
                    player.playSound(player.location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.7f, 0.3f)
                    player.playSound(player.location, Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.3f)
                    var i = 0
                    while (i < 360) {
                        val angle = Math.toRadians(i.toDouble())
                        val x = cos(angle) * 2.5
                        val z = sin(angle) * 2.5

                        val particleLoc: Location? = pivotLocation!!.clone().add(x, 0.1, z)
                        player.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 5, 0.1, 0.1, 0.1, 0.01 )
                        i += 20
                    }
                    while (i < 360) {
                        val angle = Math.toRadians(i.toDouble())
                        val x = cos(angle) * 2.5
                        val z = sin(angle) * 2.5

                        val particleLoc: Location? = pivotLocation!!.clone().add(x, 0.1, z)
                        player.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 5, 0.1, 0.1, 0.1, 0.01 )
                        i += 20
                    }

                    val earthquake = Earthquake(CinematicCore.instance)
                    earthquake.execute(
                        centerLocation = pivotLocation!!.block.location,
                        player = player,
                        type = DetectionType.CIRCULAR,
                        config = CircularConfig(2),
                        restoreTimeSeconds = 3,
                        sequential = true,
                        startFromCenter = true,
                        totalSequentialTime = 0.2f,
                        randomSize = true
                    )
                }
            }
            "test6" -> {
                if (sender !is Player) {
                    sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.")
                    return true
                }
                val player = sender

                val modelName = args.getOrNull(1)
                if (modelName.isNullOrBlank()) {
                    player.sendMessage("§c사용법: /${label} test6 <모델명>")
                    return true
                }

                val spawnResult = ModelDisplaySpawner.spawnByName(
                    plugin = CinematicCore.instance,
                    modelName = modelName,
                    location = player.location,
                    player = player,
                    scaledBoneCache = CinematicCore.instance.modelService.scaledBoneCache
                )

                if (spawnResult == null) {
                    player.sendMessage("§c모델을 불러오지 못했습니다: $modelName")
                    return true
                }

                val baseEntity = (spawnResult.baseEntity as? LivingEntity)
                if (baseEntity == null) {
                    player.sendMessage("§cBase 엔티티를 찾을 수 없습니다.")
                    return true
                }

                val receiver = AnimationEventAPI.register(baseEntity)
                val scripts = spawnResult.model.animations
                    .flatMap { it.eventKeyframes }
                    .map { it.script.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                scripts.forEach { script ->
                    receiver.on(script) { ctx ->
                        val log = "[EventKeyframe] entity=${ctx.entity.uniqueId} model=${ctx.model.modelName} animation=${ctx.animation.name} script=${ctx.keyframe.script} time=${"%.3f".format(ctx.keyframe.time)}"
                        println(log)
                        player.sendMessage("§d이벤트 §f${ctx.keyframe.script} §7(${ctx.animation.name}@${"%.2f".format(ctx.keyframe.time)})")
                    }
                }

                player.sendMessage("§a${spawnResult.model.modelName} 모델 소환 완료")
                if (scripts.isEmpty()) {
                    player.sendMessage("§e이 모델에는 EventKeyframe 데이터가 없습니다.")
                } else {
                    player.sendMessage("§7EventKeyframe 리스너 등록: ${scripts.joinToString()}")
                    player.sendMessage("§7애니메이션 재생 중 이벤트가 감지되면 콘솔에 println 됩니다.")
                }
            }
            "test7" -> {
                val player = sender as Player
                player.sendMessage("test")
                CinematicCoreAPI.Cutscene.stop(player, restoreOriginalLocation = false)
            }
            else -> {
                sender.sendMessage("§c알 수 없는 명령어입니다. 사용법: /${label} <reload|summon|disguise|undisguise|spawntest|testspawn>")
            }
        }
        
        return true
    }

    private fun startParticleTask(player: Player, mapper: BoneDisplayMapper, boneUuid: String) {
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!EntityModelUtil.hasModel(player)) {
                    return
                }
                // Lower_LeftArm bone의 pivot 위치 가져오기
                val pivotLocation = mapper.getBonePivotLocation(boneUuid) ?: return

                // 파티클 재생
                player.world.spawnParticle(
                    Particle.SOUL_FIRE_FLAME, // 원하는 파티클 타입으로 변경 가능
                    pivotLocation,
                    1, // 파티클 개수
                    0.1, // x 오프셋
                    0.1, // y 오프셋
                    0.1, // z 오프셋
                    0.0 // 속도
                )
            }
        }
        // 매 틱마다 실행 (1L = 1 tick = 0.05초)
        val bukkitTask = task.runTaskTimer(CinematicCore.instance, 0L, 1L)
    }

    fun set(player: Player, yOffset: Float, partName: String, sum: Float, zOffset: Float) : ItemDisplay {
        val entity = player.world.spawn(player.location, ItemDisplay::class.java) { entity ->
            // transformation
            entity.updateTransformation {
                translation {
                    x = 0f
                    y = yOffset
                    z = 0f
                }
            }
            entity.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND

            // rotation
            entity.setRotation(0f, 0f)

            // 아이템 설정 (플레이어 머리)
            val stack = ItemStack(Material.PLAYER_HEAD)
            val meta = stack.itemMeta as SkullMeta
            meta.itemModel = NamespacedKey.minecraft("blueprint/player_display/torso")
            meta.owningPlayer = player
            stack.itemMeta = meta
            entity.setItemStack(stack)
        }
        entity.updateTransformation {
            translation {
                x = zOffset
                y = yOffset + sum
                z = 0f
            }
        }
        return entity
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val completions = mutableListOf<String>()
        
        if (args.size == 1) {
            // 첫 번째 인자일 때 사용 가능한 서브커맨드 제안
            val subCommands = listOf("reload", "summon", "disguise", "undisguise", "testspawn", "scene")
            val input = args[0].lowercase()
            
            subCommands.forEach { subCommand ->
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand)
                }
            }
        } else if (args[0].lowercase() == "scene") {
            // scene 서브커맨드의 탭 완성은 CutsceneCommand로 위임
            val commandToUse = cutsceneCommand ?: run {
                // fallback: 인스턴스가 없으면 새로 생성 (하위 호환성)
                val cutsceneManager = org.example.hoon.cinematicCore.cutscene.CutsceneManager(CinematicCore.instance)
                val editModeManager = org.example.hoon.cinematicCore.cutscene.EditModeManager(CinematicCore.instance)
                val keyframeDisplayManager = org.example.hoon.cinematicCore.cutscene.KeyframeDisplayManager(CinematicCore.instance)
                val pathVisualizer = org.example.hoon.cinematicCore.cutscene.PathVisualizer(CinematicCore.instance)
                val cutscenePlayer = org.example.hoon.cinematicCore.cutscene.CutscenePlayer(CinematicCore.instance)
                CutsceneCommand(
                    cutsceneManager,
                    editModeManager,
                    keyframeDisplayManager,
                    pathVisualizer,
                    cutscenePlayer
                )
            }
            val subArgs = args.drop(1).toTypedArray()
            return commandToUse.onTabComplete(sender, command, alias, subArgs)
        } else if (args.size == 2 && (args[0].lowercase() == "summon" || args[0].lowercase() == "disguise" || args[0].lowercase() == "testspawn")) {
            // summon, disguise, testspawn 명령어의 두 번째 인자일 때 모델 리스트 제안
            val modelNames = CinematicCore.instance.modelFilesystem.existingModelNames()
            val input = args[1].lowercase()
            
            modelNames.forEach { modelName ->
                if (modelName.lowercase().startsWith(input)) {
                    completions.add(modelName)
                }
            }
        }
        
        return completions
    }
}

