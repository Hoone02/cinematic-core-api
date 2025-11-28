package org.example.hoon.cinematicCore.cutscene

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.components.CustomModelDataComponent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import java.util.*

/**
 * 키프레임 Sheep 생성 및 관리 클래스
 */
class KeyframeDisplayManager(private val plugin: Plugin) {
    
    // 키프레임 인덱스 -> ItemDisplay 매핑
    private val keyframeDisplays: MutableMap<Int, ItemDisplay> = mutableMapOf()
    
    // 키프레임 인덱스 -> Interaction 매핑
    private val keyframeInteractions: MutableMap<Int, Interaction> = mutableMapOf()
    
    // 플레이어별 키프레임 표시 관리
    private val playerKeyframes: MutableMap<UUID, MutableSet<Int>> = mutableMapOf()
    
    companion object {
        const val KEYFRAME_INDEX_METADATA = "cinematiccore:keyframe_index"
        const val KEYFRAME_DISPLAY_METADATA = "cinematiccore:keyframe_display"
    }
    
    /**
     * 키프레임에 대한 ItemDisplay 생성
     *
     * @param keyframe 키프레임
     * @param player 플레이어 (표시 대상)
     * @return 생성된 ItemDisplay
     */
    fun createKeyframeDisplay(keyframe: Keyframe, player: Player): ItemDisplay {
        val location = keyframe.location.clone()

        val display = location.world.spawn(location, ItemDisplay::class.java)
        display.interpolationDelay = 0
        display.teleportDuration = 1

        val item = ItemStack(Material.ENDER_PEARL)
        val meta = item.itemMeta
        meta?.setDisplayName("§e키프레임 #${keyframe.index}")
        meta?.let {
            val component: CustomModelDataComponent = it.customModelDataComponent
            component.strings = listOf("camcorder")
            it.setCustomModelDataComponent(component)
            item.itemMeta = it
        }
        display.setItemStack(item)

        // Interaction 엔티티 생성 (우클릭 감지용) - 같은 위치에 배치
        val interaction = location.world.spawn(location, Interaction::class.java)
        interaction.interactionHeight = 1.0f
        interaction.interactionWidth = 1.0f
        interaction.setMetadata(KEYFRAME_INDEX_METADATA, FixedMetadataValue(plugin, keyframe.index))
        interaction.setMetadata(KEYFRAME_DISPLAY_METADATA, FixedMetadataValue(plugin, display))

        // 매핑 저장
        keyframeDisplays[keyframe.index] = display
        keyframeInteractions[keyframe.index] = interaction

        // 플레이어별 키프레임 추가
        playerKeyframes.getOrPut(player.uniqueId) { mutableSetOf() }.add(keyframe.index)

        return display
    }
    
    /**
     * 모든 키프레임에 대한 Sheep 생성
     * 
     * @param keyframes 키프레임 리스트
     * @param player 플레이어
     */
    fun createAllKeyframeDisplays(keyframes: List<Keyframe>, player: Player) {
        keyframes.forEach { keyframe ->
            createKeyframeDisplay(keyframe, player)
        }
    }
    
    /**
     * 키프레임 Sheep 제거
     * 
     * @param index 키프레임 인덱스
     */
    fun removeKeyframeDisplay(index: Int) {
        val display = keyframeDisplays.remove(index)
        val interaction = keyframeInteractions.remove(index)
        
        display?.remove()
        interaction?.remove()
    }
    
    /**
     * 플레이어의 모든 키프레임 표시 제거
     * 
     * @param player 플레이어
     */
    fun removeAllKeyframeDisplays(player: Player) {
        val indices = playerKeyframes.remove(player.uniqueId) ?: return
        
        indices.forEach { index ->
            removeKeyframeDisplay(index)
        }
    }
    
    /**
     * 키프레임 표시 업데이트 (키프레임이 수정되었을 때)
     *
     * @param keyframe 수정된 키프레임
     * @param player 플레이어
     */
    fun updateKeyframeDisplay(keyframe: Keyframe, player: Player) {
        removeKeyframeDisplay(keyframe.index)
        createKeyframeDisplay(keyframe, player)
    }
    
    /**
     * Interaction으로부터 키프레임 인덱스 가져오기
     */
    fun getKeyframeIndex(interaction: Interaction): Int? {
        val metadata = interaction.getMetadata(KEYFRAME_INDEX_METADATA).firstOrNull()
        return metadata?.asInt()
    }
    
    /**
     * Interaction으로부터 ItemDisplay 가져오기
     */
    fun getKeyframeDisplay(interaction: Interaction): ItemDisplay? {
        val metadata = interaction.getMetadata(KEYFRAME_DISPLAY_METADATA).firstOrNull()
        return metadata?.value() as? ItemDisplay
    }
    
    /**
     * 모든 키프레임 표시 정리
     */
    fun clearAll() {
        keyframeDisplays.values.forEach { it.remove() }
        keyframeInteractions.values.forEach { it.remove() }
        keyframeDisplays.clear()
        keyframeInteractions.clear()
        playerKeyframes.clear()
    }
}

