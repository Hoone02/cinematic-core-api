package org.example.hoon.cinematicCore.cutscene

import org.bukkit.Material
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.*

/**
 * 키프레임 Sheep 우클릭 이벤트 처리 리스너
 */
class KeyframeInteractionListener(
    private val plugin: Plugin,
    private val keyframeDisplayManager: KeyframeDisplayManager,
    private val cutsceneManager: CutsceneManager,
    private val editModeManager: EditModeManager,
    private val pathVisualizer: PathVisualizer,
    private val relocationManager: KeyframeRelocationManager,
    private val timingInputManager: KeyframeTimingInputManager,
    private val pauseInputManager: KeyframePauseInputManager,
    private val smoothInputManager: KeyframeSmoothInputManager,
    private val cutscenePlayer: CutscenePlayer
) : Listener {
    
    init {
        relocationManager.registerMenuOpener { targetPlayer, index ->
            openKeyframeMenu(targetPlayer, index)
        }
        timingInputManager.registerMenuOpener { targetPlayer, index ->
            openKeyframeMenu(targetPlayer, index)
        }
        pauseInputManager.registerMenuOpener { targetPlayer, index ->
            openKeyframeMenu(targetPlayer, index)
        }
    }
    
    // 플레이어별 열린 GUI의 키프레임 인덱스 저장
    private val openMenus: MutableMap<UUID, Int> = mutableMapOf()
    
    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val clicked = event.rightClicked
        
        // Interaction 엔티티인지 확인
        if (clicked !is Interaction) {
            return
        }
        
        // 키프레임 Interaction인지 확인
        val keyframeIndex = keyframeDisplayManager.getKeyframeIndex(clicked) ?: return
        
        // 수정모드에 있는지 확인
        if (!editModeManager.isInEditMode(player)) {
            return
        }
        
        event.isCancelled = true
        
        // GUI 메뉴 열기
        openKeyframeMenu(player, keyframeIndex)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // 키프레임 설정 GUI인지 확인 (view.title 사용)
        val title = event.view.title
        if (!title.startsWith("§e키프레임 #")) {
            return
        }
        
        event.isCancelled = true
        
        val keyframeIndex = openMenus[player.uniqueId] ?: return
        val clickedItem = event.currentItem ?: return
        
        when (event.slot) {
            4 -> {
                // 정보 아이템 (클릭 불가)
                return
            }
            10 -> {
                // 키프레임 추가
                addKeyframeAfter(player, keyframeIndex)
                player.closeInventory()
            }
            11 -> {
                // 위치 수정
                startKeyframeRelocation(player, keyframeIndex)
            }
            12 -> {
                // 선형 보간 선택
                updateKeyframeInterpolation(player, keyframeIndex, InterpolationType.LINEAR)
                player.closeInventory()
                player.sendActionBar("§a키프레임 #$keyframeIndex 보간 방식을 선형으로 변경했습니다.")
            }
            13 -> {
                // 베지어 보간 선택
                updateKeyframeInterpolation(player, keyframeIndex, InterpolationType.BEZIER)
                player.closeInventory()
                player.sendActionBar("§a키프레임 #$keyframeIndex 보간 방식을 베지어로 변경했습니다.")
            }
            14 -> {
                // 구간 시간 설정
                startTimingInput(player, keyframeIndex)
            }
            15 -> {
                // 멈춤 시간 설정
                startPauseInput(player, keyframeIndex)
            }
            16 -> {
                // 테스트 재생 시작
                startTestPlayFromKeyframe(player, keyframeIndex)
                player.closeInventory()
            }
            21 -> {
                // 시작 smooth 토글
                toggleStartSmooth(player, keyframeIndex)
                player.closeInventory()
                openKeyframeMenu(player, keyframeIndex)
            }
            23 -> {
                // 끝 smooth 토글
                toggleEndSmooth(player, keyframeIndex)
                player.closeInventory()
                openKeyframeMenu(player, keyframeIndex)
            }
            35 -> {
                // 삭제
                deleteKeyframe(player, keyframeIndex)
                player.closeInventory()
                player.sendActionBar("§a키프레임 #$keyframeIndex 삭제되었습니다.")
            }
        }
    }

    /**
     * 키프레임 설정 GUI 메뉴 열기
     */
    fun openKeyframeMenu(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        val keyframe = cutscene.keyframes.find { it.index == keyframeIndex } ?: return
        
        // GUI 인벤토리 생성 (36칸 - 4줄)
        val inventory = plugin.server.createInventory(null, 36, "§e키프레임 #$keyframeIndex 설정")
        
        // 정보 표시 아이템
        val infoItem = ItemStack(Material.PAPER)
        val infoMeta = infoItem.itemMeta
        infoMeta?.setDisplayName("§a키프레임 정보")
        val interpolationTypeName = when (keyframe.interpolationType) {
            InterpolationType.LINEAR -> "선형"
            InterpolationType.BEZIER -> "베지어"
        }
        val durationLore = if (keyframe.durationSeconds != null) {
            "§7구간 시간: §f${"%.2f".format(keyframe.durationSeconds)}초"
        } else {
            "§7구간 시간: §f자동(거리 기반)"
        }
        val pauseLore = if (keyframe.pauseSeconds != null) {
            "§7멈춤 시간: §f${"%.2f".format(keyframe.pauseSeconds)}초"
        } else {
            "§7멈춤 시간: §f없음"
        }

        infoMeta?.lore = listOf(
            "§7인덱스: §f$keyframeIndex",
            "§7위치: §f${keyframe.location.blockX}, ${keyframe.location.blockY}, ${keyframe.location.blockZ}",
            "§7월드: §f${keyframe.location.world?.name}",
            "§7Pitch: §f${keyframe.pitch.toInt()}°",
            "§7Yaw: §f${keyframe.yaw.toInt()}°",
            "§7보간 방식: §f$interpolationTypeName",
            durationLore,
            pauseLore
        )
        // 가장 위 중앙: 키프레임 정보
        infoItem.itemMeta = infoMeta
        inventory.setItem(4, infoItem)
        
        // 두 번째 줄: 첫 번째 비워놓고, 키프레임 추가, 위치 수정, 선형 보간, 베지어 보간, 구간 시간 설정, 멈춤 시간 설정
        // 슬롯 9는 비워둠
        val addItem = ItemStack(Material.NETHER_STAR)
        val addMeta = addItem.itemMeta
        addMeta?.setDisplayName("§a키프레임 추가")
        addMeta?.lore = listOf(
            "§7이 키프레임 뒤에 새 키프레임을",
            "§7현재 위치를 기준으로 추가합니다."
        )
        addItem.itemMeta = addMeta
        inventory.setItem(10, addItem)
        
        // 위치 수정 아이템
        val moveItem = ItemStack(Material.COMPASS)
        val moveMeta = moveItem.itemMeta
        moveMeta?.setDisplayName("§b위치 수정")
        moveMeta?.lore = listOf(
            "§7클릭하여 현재 위치로",
            "§7키프레임을 이동합니다."
        )
        moveItem.itemMeta = moveMeta
        inventory.setItem(11, moveItem)
        
        // 선형 보간
        val linearItem = ItemStack(Material.GREEN_WOOL)
        val linearMeta = linearItem.itemMeta
        val isLinear = keyframe.interpolationType == InterpolationType.LINEAR
        linearMeta?.setDisplayName(if (isLinear) "§a선형 보간 §7(선택됨)" else "§7선형 보간")
        linearMeta?.lore = listOf(
            "§7키프레임 사이를 직선으로",
            "§7이동합니다.",
            if (isLinear) "§a✓ 현재 선택됨" else "§7클릭하여 선택"
        )
        linearItem.itemMeta = linearMeta
        inventory.setItem(12, linearItem)
        
        // 베지어 보간
        val bezierItem = ItemStack(Material.BLUE_WOOL)
        val bezierMeta = bezierItem.itemMeta
        val isBezier = keyframe.interpolationType == InterpolationType.BEZIER
        bezierMeta?.setDisplayName(if (isBezier) "§b베지어 보간 §7(선택됨)" else "§7베지어 보간")
        bezierMeta?.lore = listOf(
            "§7키프레임 사이를 부드러운",
            "§7곡선으로 이동합니다.",
            if (isBezier) "§a✓ 현재 선택됨" else "§7클릭하여 선택"
        )
        bezierItem.itemMeta = bezierMeta
        inventory.setItem(13, bezierItem)
        
        // 구간 시간 설정
        val timingItem = ItemStack(Material.CLOCK)
        val timingMeta = timingItem.itemMeta
        timingMeta?.setDisplayName("§6구간 시간 설정")
        timingMeta?.lore = listOf(
            "§7이 구간을 이동하는 데 걸리는",
            "§7시간(초)을 직접 설정합니다.",
            if (keyframe.durationSeconds != null)
                "§e현재: ${"%.2f".format(keyframe.durationSeconds)}초 (직접 설정)"
            else
                "§e현재: 자동 (거리 기반)",
            "§7클릭 후 채팅으로 시간을 입력하세요.",
            "§7'auto' 또는 0 입력 시 자동으로 전환됩니다."
        )
        timingItem.itemMeta = timingMeta
        inventory.setItem(14, timingItem)
        
        // 멈춤 시간 설정
        val pauseItem = ItemStack(Material.REPEATER)
        val pauseMeta = pauseItem.itemMeta
        pauseMeta?.setDisplayName("§d멈춤 시간 설정")
        pauseMeta?.lore = listOf(
            "§7이 키프레임에 도달했을 때",
            "§7멈출 시간(초)을 설정합니다.",
            if (keyframe.pauseSeconds != null)
                "§e현재: ${"%.2f".format(keyframe.pauseSeconds)}초"
            else
                "§e현재: 없음",
            "§7클릭 후 채팅으로 시간을 입력하세요.",
            "§7'0' 입력 시 멈춤 시간을 제거합니다."
        )
        pauseItem.itemMeta = pauseMeta
        inventory.setItem(15, pauseItem)
        
        // 두번째 줄 7번째 칸: 테스트 재생 시작
        val testPlayItem = ItemStack(Material.PRISMARINE_SHARD)
        val testPlayMeta = testPlayItem.itemMeta
        testPlayMeta?.setDisplayName("§e테스트 재생 시작")
        testPlayMeta?.lore = listOf(
            "§7이 키프레임부터",
            "§7테스트 재생을 시작합니다."
        )
        testPlayItem.itemMeta = testPlayMeta
        inventory.setItem(16, testPlayItem)
        
        // 시작 smooth 아이템 (세번째 줄 네번째 칸)
        val startSmoothItem = ItemStack(if (keyframe.startSmooth) Material.LIME_DYE else Material.GRAY_DYE)
        val startSmoothMeta = startSmoothItem.itemMeta
        startSmoothMeta?.setDisplayName(if (keyframe.startSmooth) "§a시작 Smooth §7(ON)" else "§7시작 Smooth §7(OFF)")
        startSmoothMeta?.lore = listOf(
            "§7이 키프레임의 시작을",
            "§7부드럽게 만듭니다.",
            if (keyframe.startSmooth)
                "§a현재: ON"
            else
                "§7현재: OFF",
            "§7클릭하여 토글합니다."
        )
        startSmoothItem.itemMeta = startSmoothMeta
        inventory.setItem(21, startSmoothItem)
        
        // 끝 smooth 아이템 (세번째 줄 여섯번째 칸)
        val endSmoothItem = ItemStack(if (keyframe.endSmooth) Material.LIME_DYE else Material.GRAY_DYE)
        val endSmoothMeta = endSmoothItem.itemMeta
        endSmoothMeta?.setDisplayName(if (keyframe.endSmooth) "§a끝 Smooth §7(ON)" else "§7끝 Smooth §7(OFF)")
        endSmoothMeta?.lore = listOf(
            "§7다음 키프레임 도달 시",
            "§7부드럽게 끝나도록 합니다.",
            if (keyframe.endSmooth)
                "§a현재: ON"
            else
                "§7현재: OFF",
            "§7클릭하여 토글합니다."
        )
        endSmoothItem.itemMeta = endSmoothMeta
        inventory.setItem(23, endSmoothItem)
        
        // 가장 밑 오른쪽: 삭제
        val deleteItem = ItemStack(Material.BARRIER)
        val deleteMeta = deleteItem.itemMeta
        deleteMeta?.setDisplayName("§c삭제")
        deleteMeta?.lore = listOf(
            "§7클릭하여 이 키프레임을",
            "§7삭제합니다."
        )
        deleteItem.itemMeta = deleteMeta
        inventory.setItem(35, deleteItem)
        
        openMenus[player.uniqueId] = keyframeIndex
        player.openInventory(inventory)
    }
    
    /**
     * 특정 키프레임부터 테스트 재생 시작
     */
    private fun startTestPlayFromKeyframe(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        
        if (cutscene.keyframes.isEmpty()) {
            player.sendActionBar("§c키프레임이 없습니다.")
            return
        }
        
        // 해당 키프레임이 존재하는지 확인
        val keyframe = cutscene.keyframes.find { it.index == keyframeIndex }
        if (keyframe == null) {
            player.sendActionBar("§c키프레임을 찾을 수 없습니다: #$keyframeIndex")
            return
        }
        
        // 해당 키프레임부터 재생할 수 있는지 확인 (최소 2개 필요)
        val remainingKeyframes = cutscene.keyframes.dropWhile { it.index < keyframeIndex }
        if (remainingKeyframes.size < 2) {
            player.sendActionBar("§c재생할 키프레임이 부족합니다.")
            return
        }
        
        player.sendActionBar("§a테스트 재생 시작: 키프레임 #$keyframeIndex 부터")
        cutscenePlayer.play(cutscene, player, startFromIndex = keyframeIndex)
    }
    
    /**
     * 위치 수정 세션 시작
     */
    private fun startKeyframeRelocation(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        
        val keyframe = cutscene.keyframes.find { it.index == keyframeIndex } ?: return
        relocationManager.startRelocation(player, sceneName, keyframe)
        openMenus.remove(player.uniqueId)
    }
    
    private fun startTimingInput(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        if (!cutsceneManager.exists(sceneName)) {
            player.sendActionBar("§c컷씬을 찾을 수 없습니다.")
            return
        }
        timingInputManager.requestTimingInput(player, sceneName, keyframeIndex)
        openMenus.remove(player.uniqueId)
        player.closeInventory()
    }
    
    private fun startPauseInput(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        if (!cutsceneManager.exists(sceneName)) {
            player.sendActionBar("§c컷씬을 찾을 수 없습니다.")
            return
        }
        pauseInputManager.requestPauseInput(player, sceneName, keyframeIndex)
        openMenus.remove(player.uniqueId)
        player.closeInventory()
    }
    
    private fun toggleStartSmooth(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        if (!cutsceneManager.exists(sceneName)) {
            player.sendActionBar("§c컷씬을 찾을 수 없습니다.")
            return
        }
        if (smoothInputManager.toggleStartSmooth(player, sceneName, keyframeIndex)) {
            val cutscene = cutsceneManager.load(sceneName)
            val keyframe = cutscene?.keyframes?.find { it.index == keyframeIndex }
            if (keyframe?.startSmooth == true) {
                player.sendActionBar("§a키프레임 #$keyframeIndex 시작 Smooth: ON")
            } else {
                player.sendActionBar("§7키프레임 #$keyframeIndex 시작 Smooth: OFF")
            }
        } else {
            player.sendActionBar("§c시작 Smooth 토글에 실패했습니다.")
        }
    }
    
    private fun toggleEndSmooth(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        if (!cutsceneManager.exists(sceneName)) {
            player.sendActionBar("§c컷씬을 찾을 수 없습니다.")
            return
        }
        if (smoothInputManager.toggleEndSmooth(player, sceneName, keyframeIndex)) {
            val cutscene = cutsceneManager.load(sceneName)
            val keyframe = cutscene?.keyframes?.find { it.index == keyframeIndex }
            if (keyframe?.endSmooth == true) {
                player.sendActionBar("§a키프레임 #$keyframeIndex 끝 Smooth: ON")
            } else {
                player.sendActionBar("§7키프레임 #$keyframeIndex 끝 Smooth: OFF")
            }
        } else {
            player.sendActionBar("§c끝 Smooth 토글에 실패했습니다.")
        }
    }
    
    private fun addKeyframeAfter(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return

        val location = player.location.clone()
        val insertIndex = keyframeIndex + 1

        val newKeyframe = Keyframe(
            index = insertIndex,
            location = location,
            pitch = location.pitch,
            yaw = location.yaw,
            interpolationType = InterpolationType.LINEAR
        )

        val newKeyframes = mutableListOf<Keyframe>()
        cutscene.keyframes.forEach { existing ->
            if (existing.index <= keyframeIndex) {
                newKeyframes.add(existing)
            } else {
                newKeyframes.add(existing.copy(index = existing.index + 1))
            }
        }
        newKeyframes.add(insertIndex, newKeyframe)
        val updatedCutscene = Cutscene(sceneName, newKeyframes.sortedBy { it.index })

        if (cutsceneManager.save(updatedCutscene)) {
            keyframeDisplayManager.removeAllKeyframeDisplays(player)
            keyframeDisplayManager.createAllKeyframeDisplays(updatedCutscene.keyframes, player)
            pathVisualizer.refreshPath(player, updatedCutscene.keyframes)
            player.sendActionBar("§a키프레임 #$insertIndex 가 추가되었습니다.")
        } else {
            player.sendActionBar("§c키프레임 추가에 실패했습니다.")
        }
    }
    
    /**
     * 키프레임 보간 방식 업데이트
     */
    private fun updateKeyframeInterpolation(player: Player, keyframeIndex: Int, interpolationType: InterpolationType) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        
        val keyframe = cutscene.keyframes.find { it.index == keyframeIndex } ?: return
        
        // 새로운 키프레임 생성 (보간 방식만 변경)
        val newKeyframe = keyframe.copy(interpolationType = interpolationType)
        
        // 키프레임 리스트 업데이트
        val newKeyframes = cutscene.keyframes.map { if (it.index == keyframeIndex) newKeyframe else it }
        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)
        
        // 저장
        cutsceneManager.save(updatedCutscene)
        
        // 표시 업데이트
        keyframeDisplayManager.updateKeyframeDisplay(newKeyframe, player)
        pathVisualizer.refreshPath(player, updatedCutscene.keyframes)
    }
    
    /**
     * 키프레임 삭제
     */
    private fun deleteKeyframe(player: Player, keyframeIndex: Int) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        
        // 키프레임 제거
        val newKeyframes = cutscene.keyframes.filter { it.index != keyframeIndex }
            .mapIndexed { newIndex, keyframe ->
                // 인덱스 재정렬 (보간 방식 유지)
                keyframe.copy(index = newIndex)
            }
        
        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)
        
        // 저장
        cutsceneManager.save(updatedCutscene)
        
        // 표시 제거 및 재생성
        keyframeDisplayManager.removeKeyframeDisplay(keyframeIndex)
        keyframeDisplayManager.removeAllKeyframeDisplays(player)
        keyframeDisplayManager.createAllKeyframeDisplays(newKeyframes, player)
        pathVisualizer.refreshPath(player, newKeyframes)
    }
}

