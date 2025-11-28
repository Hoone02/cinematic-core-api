package org.example.hoon.cinematicCore.cutscene

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.*

/**
 * 수정모드 관리 클래스
 * 플레이어 인벤토리 저장/복원 및 수정모드 전용 아이템 지급
 */
class EditModeManager(private val plugin: Plugin) {
    
    // NamespacedKey (lazy 초기화)
    private val editModeItemKey: NamespacedKey by lazy {
        NamespacedKey(plugin, "edit_mode_item")
    }
    
    private val editModeItemTypeKey: NamespacedKey by lazy {
        NamespacedKey(plugin, "edit_mode_item_type")
    }
    
    // 플레이어별 저장된 인벤토리
    private val savedInventories: MutableMap<UUID, Array<ItemStack?>> = mutableMapOf()
    
    // 수정모드에 있는 플레이어들
    private val editModePlayers: MutableSet<UUID> = mutableSetOf()
    
    // 플레이어별 현재 편집 중인 컷씬
    private val editingScenes: MutableMap<UUID, String> = mutableMapOf()
    
    /**
     * 수정모드 진입
     * 
     * @param player 플레이어
     * @param sceneName 편집할 컷씬 이름
     * @return 성공 여부
     */
    fun enterEditMode(player: Player, sceneName: String): Boolean {
        
        if (editModePlayers.contains(player.uniqueId)) {
            player.sendActionBar("§c이미 수정모드에 있습니다.")
            return false
        }
        
        // 인벤토리 저장
        saveInventory(player)
        
        // 인벤토리 비우기
        player.inventory.clear()
        
        // 수정모드 아이템 지급
        giveEditModeItems(player)
        
        // 수정모드 상태 저장
        editModePlayers.add(player.uniqueId)
        editingScenes[player.uniqueId] = sceneName
        
        player.sendActionBar("§a수정모드 진입: §f$sceneName")
        
        return true
    }
    
    /**
     * 수정모드 종료
     * 
     * @param player 플레이어
     * @return 성공 여부
     */
    fun exitEditMode(player: Player): Boolean {
        if (!editModePlayers.contains(player.uniqueId)) {
            return false
        }
        
        // 인벤토리 복원
        restoreInventory(player)
        
        // 수정모드 상태 제거
        editModePlayers.remove(player.uniqueId)
        editingScenes.remove(player.uniqueId)
        
        player.sendActionBar("§a수정모드 종료")
        
        return true
    }
    
    /**
     * 플레이어가 수정모드에 있는지 확인
     */
    fun isInEditMode(player: Player): Boolean {
        val result = editModePlayers.contains(player.uniqueId)
        return result
    }
    
    /**
     * 플레이어가 편집 중인 컷씬 이름 반환
     */
    fun getEditingScene(player: Player): String? {
        return editingScenes[player.uniqueId]
    }
    
    /**
     * 인벤토리 저장
     */
    private fun saveInventory(player: Player) {
        val inventory = player.inventory
        val contents = arrayOfNulls<ItemStack>(inventory.size)
        
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i)
            contents[i] = item?.clone()
        }
        
        savedInventories[player.uniqueId] = contents
    }
    
    /**
     * 인벤토리 복원
     */
    private fun restoreInventory(player: Player) {
        val saved = savedInventories.remove(player.uniqueId) ?: return
        
        player.inventory.clear()
        
        for (i in saved.indices) {
            val item = saved[i]
            if (item != null) {
                player.inventory.setItem(i, item)
            }
        }
        
        player.updateInventory()
    }
    
    /**
     * 수정모드 기본 아이템 다시 지급
     */
    fun applyDefaultEditItems(player: Player) {
        player.inventory.clear()
        giveEditModeItems(player)
    }

    /**
     * 위치 수정용 확정/취소 아이템 지급
     */
    fun applyRelocationItems(player: Player) {
        val inventory = player.inventory
        inventory.clear()

        inventory.setItem(3, createRelocationConfirmItem())
        inventory.setItem(5, createRelocationCancelItem())
        player.updateInventory()
    }

    /**
     * 수정모드 전용 아이템 지급
     */
    private fun giveEditModeItems(player: Player) {
        val inventory = player.inventory
        
        // 슬롯 0: 키프레임 추가
        inventory.setItem(0, createKeyframeAddItem())
        
        // 슬롯 1: 경로보기
        inventory.setItem(1, createPathToggleItem())
        
        // 슬롯 2: 테스트재생
        inventory.setItem(2, createTestPlayItem())
        
        // 슬롯 8: 수정종료
        inventory.setItem(8, createExitEditModeItem())
        
        player.updateInventory()
    }
    
    /**
     * 키프레임 추가 아이템 생성
     */
    private fun createKeyframeAddItem(): ItemStack {
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta
        meta?.setDisplayName("§a키프레임 추가")
        meta?.lore = listOf(
            "§7우클릭하여 현재 위치에",
            "§7키프레임을 추가합니다."
        )
        // 커스텀 NBT 태그 추가
        meta?.persistentDataContainer?.set(editModeItemKey, PersistentDataType.BYTE, 1)
        meta?.persistentDataContainer?.set(editModeItemTypeKey, PersistentDataType.STRING, "add_keyframe")
        item.itemMeta = meta
        return item
    }
    
    /**
     * 경로보기 토글 아이템 생성
     */
    private fun createPathToggleItem(): ItemStack {
        val item = ItemStack(Material.STRING)
        val meta = item.itemMeta
        meta?.setDisplayName("§b경로보기")
        meta?.lore = listOf(
            "§7우클릭하여 카메라 경로를",
            "§7표시/숨깁니다."
        )
        // 커스텀 NBT 태그 추가
        meta?.persistentDataContainer?.set(editModeItemKey, PersistentDataType.BYTE, 1)
        meta?.persistentDataContainer?.set(editModeItemTypeKey, PersistentDataType.STRING, "toggle_path")
        item.itemMeta = meta
        return item
    }
    
    /**
     * 테스트재생 아이템 생성
     */
    private fun createTestPlayItem(): ItemStack {
        val item = ItemStack(Material.PRISMARINE_SHARD)
        val meta = item.itemMeta
        meta?.setDisplayName("§e테스트재생")
        meta?.lore = listOf(
            "§7우클릭하여 현재 키프레임으로",
            "§7컷씬을 테스트 재생합니다."
        )
        // 커스텀 NBT 태그 추가
        meta?.persistentDataContainer?.set(editModeItemKey, PersistentDataType.BYTE, 1)
        meta?.persistentDataContainer?.set(editModeItemTypeKey, PersistentDataType.STRING, "test_play")
        item.itemMeta = meta
        return item
    }
    
    /**
     * 수정종료 아이템 생성
     */
    private fun createExitEditModeItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta
        meta?.setDisplayName("§c수정종료")
        meta?.lore = listOf(
            "§7우클릭하여 수정모드를 종료하고",
            "§7변경사항을 저장합니다."
        )
        // 커스텀 NBT 태그 추가
        meta?.persistentDataContainer?.set(editModeItemKey, PersistentDataType.BYTE, 1)
        meta?.persistentDataContainer?.set(editModeItemTypeKey, PersistentDataType.STRING, "exit_edit")
        item.itemMeta = meta
        return item
    }
    
    private fun createRelocationConfirmItem(): ItemStack {
        val item = ItemStack(Material.LIME_DYE)
        val meta = item.itemMeta
        meta?.setDisplayName("§a위치 확정")
        meta?.lore = listOf(
            "§7현재 위치로 키프레임을 이동합니다."
        )
        meta?.persistentDataContainer?.set(editModeItemKey, PersistentDataType.BYTE, 1)
        meta?.persistentDataContainer?.set(editModeItemTypeKey, PersistentDataType.STRING, "relocate_confirm")
        item.itemMeta = meta
        return item
    }
    
    private fun createRelocationCancelItem(): ItemStack {
        val item = ItemStack(Material.RED_DYE)
        val meta = item.itemMeta
        meta?.setDisplayName("§c위치 취소")
        meta?.lore = listOf(
            "§7위치 수정을 중단하고 원래 상태로 되돌립니다."
        )
        meta?.persistentDataContainer?.set(editModeItemKey, PersistentDataType.BYTE, 1)
        meta?.persistentDataContainer?.set(editModeItemTypeKey, PersistentDataType.STRING, "relocate_cancel")
        item.itemMeta = meta
        return item
    }
    
    /**
     * 아이템이 수정모드 아이템인지 확인
     */
    fun isEditModeItem(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) {
            return false
        }
        val meta = item.itemMeta
        if (meta == null) {
            return false
        }
        val hasKey = meta.persistentDataContainer.has(editModeItemKey, PersistentDataType.BYTE)
        return hasKey
    }
    
    /**
     * 수정모드 아이템 타입 가져오기
     */
    fun getEditModeItemType(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) {
            return null
        }
        val meta = item.itemMeta ?: run {
            return null
        }
        val itemType = meta.persistentDataContainer.get(editModeItemTypeKey, PersistentDataType.STRING)
        return itemType
    }
    
    /**
     * 플레이어가 오프라인되었을 때 정리
     */
    fun cleanup(player: Player) {
        savedInventories.remove(player.uniqueId)
        editModePlayers.remove(player.uniqueId)
        editingScenes.remove(player.uniqueId)
    }
}

