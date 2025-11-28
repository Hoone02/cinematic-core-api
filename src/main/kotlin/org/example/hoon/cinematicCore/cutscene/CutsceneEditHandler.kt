package org.example.hoon.cinematicCore.cutscene

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/**
 * 수정모드 아이템 클릭 이벤트 처리
 */
class CutsceneEditHandler(
    private val plugin: Plugin,
    private val editModeManager: EditModeManager,
    private val cutsceneManager: CutsceneManager,
    private val keyframeDisplayManager: KeyframeDisplayManager,
    private val pathVisualizer: PathVisualizer,
    private val cutscenePlayer: CutscenePlayer,
    private val relocationManager: KeyframeRelocationManager,
    private val timingInputManager: KeyframeTimingInputManager,
    private val pauseInputManager: KeyframePauseInputManager
) : Listener {
    
    // 수정모드 아이템 슬롯
    private val editModeSlots = setOf(0, 1, 2, 3, 5, 8)
    
    /**
     * 인벤토리 내 아이템 클릭 처리 (우클릭 포함)
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // 수정모드에 있는지 확인
        if (!editModeManager.isInEditMode(player)) {
            return
        }
        
        // 플레이어 인벤토리인지 확인 (하단 인벤토리가 플레이어 인벤토리)
        val isPlayerInventory = event.view.bottomInventory.type == InventoryType.PLAYER || 
                                event.inventory.type == InventoryType.PLAYER
        
        if (!isPlayerInventory) {
            return
        }
        
        val rawSlot = event.rawSlot
        val clickedItem = event.currentItem
        val cursorItem = event.cursor
        
        // 수정모드 아이템인지 확인 (NBT 태그로 확인)
        val clickedIsEditModeItem = clickedItem != null && editModeManager.isEditModeItem(clickedItem)
        val cursorIsEditModeItem = cursorItem != null && editModeManager.isEditModeItem(cursorItem)
        
        // 수정모드 아이템이 포함된 모든 클릭 차단 (보호)
        if (clickedIsEditModeItem || cursorIsEditModeItem) {
            event.isCancelled = true
            
            // 수정모드 아이템 슬롯에서 우클릭인 경우에만 처리
            val isEditModeSlot = rawSlot in editModeSlots || (rawSlot >= 36 && rawSlot < 45 && (rawSlot - 36) in editModeSlots)
            
            if (isEditModeSlot && event.click.isRightClick && clickedIsEditModeItem) {
                val itemType = editModeManager.getEditModeItemType(clickedItem)
                if (itemType != null) {
                    handleEditModeItemClickByType(player, itemType)
                } else {
                    // 하위 호환성: Material 기반 처리
                    handleEditModeItemClick(player, clickedItem!!.type)
                }
            }
        }
    }
    
    /**
     * 인벤토리 드래그 처리 (수정모드 아이템 보호)
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // 수정모드에 있는지 확인
        if (!editModeManager.isInEditMode(player)) {
            return
        }
        
        // 플레이어 인벤토리인지 확인
        val isPlayerInventory = event.view.bottomInventory.type == InventoryType.PLAYER || 
                                event.inventory.type == InventoryType.PLAYER
        
        if (!isPlayerInventory) {
            return
        }
        
        // 수정모드 아이템 슬롯에 드래그하려는 경우 차단
        val rawSlots = event.rawSlots
        if (rawSlots.any { it in editModeSlots }) {
            event.isCancelled = true
        }
        
        // 수정모드 아이템을 드래그하려는 경우도 차단
        val draggedItem = event.oldCursor
        if (draggedItem != null && editModeManager.isEditModeItem(draggedItem)) {
            event.isCancelled = true
        }
    }
    
    /**
     * 아이템 드롭 처리 (수정모드 아이템 보호)
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        
        // 수정모드에 있는지 확인
        if (!editModeManager.isInEditMode(player)) {
            return
        }
        
        val droppedItem = event.itemDrop.itemStack
        
        // 수정모드 아이템인지 확인 (NBT 태그로 확인)
        if (editModeManager.isEditModeItem(droppedItem)) {
            event.isCancelled = true
        }
    }
    
    /**
     * 손에 든 아이템 우클릭 처리 (아이템 사용)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        
        // 디버깅: 이벤트 발생 확인
        
        // 수정모드에 있는지 확인
        if (!editModeManager.isInEditMode(player)) {
            return
        }
        
        // 손에 든 아이템 확인 (우선 확인)
        val item = player.inventory.itemInMainHand
        
        if (item.type == Material.AIR) {
            return
        }
        
        // 수정모드 아이템인지 확인 (NBT 태그로 확인)
        val isEditModeItem = editModeManager.isEditModeItem(item)
        
        if (!isEditModeItem) {
            return
        }
        
        // 우클릭만 처리
        
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        
        // 아이템 사용 차단
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
        event.isCancelled = true
        
        // 아이템 타입 가져오기
        val itemType = editModeManager.getEditModeItemType(item)
        
        if (itemType != null) {
            // 다음 틱에 실행 (이벤트 처리 후)
            plugin.server.scheduler.runTask(plugin, Runnable {
                handleEditModeItemClickByType(player, itemType)
            })
        } else {
        }
    }

    /**
     * Shift(웅크리기) 입력 시 테스트 재생 중단
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player

        if (!event.isSneaking) {
            return
        }

        if (!editModeManager.isInEditMode(player)) {
            return
        }

        if (!cutscenePlayer.playingPlayers.containsKey(player)) {
            return
        }

        cutscenePlayer.stop(player)
        player.sendActionBar("§c테스트 재생을 중단했습니다.")
    }

    /**
     * 컷씬 재생 중 빙의 해제 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerStopSpectatingEntity(event: PlayerStopSpectatingEntityEvent) {
        val player = event.player

        // 컷씬 재생 중인지 확인
        if (cutscenePlayer.playingPlayers.containsKey(player)) {
            event.isCancelled = true
        }
    }
    
    /**
     * 수정모드 아이템인지 확인 (Material 기반 - 하위 호환성)
     */
    private fun isEditModeItem(material: Material): Boolean {
        return material == Material.EMERALD ||
               material == Material.COMPASS ||
               material == Material.PLAYER_HEAD ||
               material == Material.BARRIER
    }
    
    /**
     * 수정모드 아이템 클릭 처리 (타입 기반)
     */
    private fun handleEditModeItemClickByType(player: Player, itemType: String) {
        when (itemType) {
            "add_keyframe" -> {
                // 키프레임 추가
                addKeyframe(player)
            }
            "toggle_path" -> {
                // 경로보기 토글
                togglePath(player)
            }
            "test_play" -> {
                // 테스트재생
                testPlay(player)
            }
            "exit_edit" -> {
                // 수정종료
                exitEditMode(player)
            }
            "relocate_confirm" -> {
                if (!relocationManager.confirmRelocation(player)) {
                    player.sendActionBar("§c위치 수정 세션이 없습니다.")
                }
            }
            "relocate_cancel" -> {
                if (!relocationManager.cancelRelocation(player)) {
                    player.sendActionBar("§c취소할 위치 수정 세션이 없습니다.")
                }
            }
            else -> return
        }
    }
    
    /**
     * 수정모드 아이템 클릭 처리 (Material 기반 - 하위 호환성)
     */
    private fun handleEditModeItemClick(player: Player, material: Material) {
        when (material) {
            Material.GREEN_CONCRETE -> {
                // 키프레임 추가
                addKeyframe(player)
            }
            Material.BLUE_CONCRETE -> {
                // 경로보기 토글
                togglePath(player)
            }
            Material.YELLOW_CONCRETE -> {
                // 테스트재생
                testPlay(player)
            }
            Material.RED_CONCRETE -> {
                // 수정종료
                exitEditMode(player)
            }
            else -> return
        }
    }
    
    /**
     * 키프레임 추가
     */
    private fun addKeyframe(player: Player) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: Cutscene(sceneName, emptyList())
        
        val location = player.location.clone()
        val newIndex = cutscene.keyframes.size
        
        val newKeyframe = Keyframe(
            index = newIndex,
            location = location,
            pitch = location.pitch,
            yaw = location.yaw,
            interpolationType = InterpolationType.LINEAR // 기본값: 선형
        )
        
        val newKeyframes = cutscene.keyframes + newKeyframe
        val updatedCutscene = Cutscene(cutscene.name, newKeyframes)
        
        if (cutsceneManager.save(updatedCutscene)) {
            // 키프레임 표시 추가
            keyframeDisplayManager.createKeyframeDisplay(newKeyframe, player)
            pathVisualizer.refreshPath(player, updatedCutscene.keyframes)
            player.sendActionBar("§a키프레임 #$newIndex 추가됨")
        } else {
            player.sendActionBar("§c키프레임 추가 실패")
        }
    }
    
    /**
     * 경로보기 토글
     */
    private fun togglePath(player: Player) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        
        val isVisible = pathVisualizer.togglePath(player, cutscene.keyframes)
        
        if (isVisible) {
            player.sendActionBar("§a경로보기: §fON")
        } else {
            player.sendActionBar("§7경로보기: §fOFF")
        }
    }
    
    /**
     * 테스트재생
     */
    private fun testPlay(player: Player) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        val cutscene = cutsceneManager.load(sceneName) ?: return
        
        if (cutscene.keyframes.isEmpty()) {
            player.sendActionBar("§c키프레임이 없습니다.")
            return
        }
        
        player.sendActionBar("§a테스트재생 시작...")
        cutscenePlayer.play(cutscene, player)
    }
    
    /**
     * 수정모드 종료
     */
    private fun exitEditMode(player: Player) {
        val sceneName = editModeManager.getEditingScene(player) ?: return
        
        relocationManager.abort(player, restoreDisplay = false)
        timingInputManager.abort(player)
        pauseInputManager.abort(player)
        cutscenePlayer.playingPlayers[player]?.let { state ->
            cutscenePlayer.stop(player)
            player.sendActionBar("§c수정모드 종료로 인해 진행 중이던 테스트 재생을 중단했습니다.")
        }
        
        // 키프레임 표시 제거
        keyframeDisplayManager.removeAllKeyframeDisplays(player)
        
        // 경로 표시 중지
        pathVisualizer.cleanup(player)
        
        // 수정모드 종료
        editModeManager.exitEditMode(player)
        
        player.sendActionBar("§a수정모드 종료: §f$sceneName")
    }
}

