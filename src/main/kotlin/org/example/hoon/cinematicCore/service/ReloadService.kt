package org.example.hoon.cinematicCore.service

import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.util.prefix

/**
 * 리로드 서비스 클래스
 * 모델 리로드 로직을 담당하는 비즈니스 로직
 */
class ReloadService(
    private val plugin: CinematicCore
) {
    
    /**
     * 메시지 전송 후 소리 재생
     */
    private fun sendMessageWithSound(sender: CommandSender, message: String) {
        sender.sendMessage(message)
        if (sender is Player) {
            sender.playSound(sender.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.8f)
        }
    }
    
    /**
     * 리로드 실행
     * 
     * @param sender 명령어 실행자
     * @return 리로드 성공 여부
     */
    fun reload(sender: CommandSender): Boolean {
        val startTime = System.currentTimeMillis()
        sendMessageWithSound(sender, "§7${prefix} 리로드 시작...")
        
        return try {
            // 1단계: 삭제된 모델 정리
            sendMessageWithSound(sender, "§7${prefix} 삭제된 모델 정리 중...")
            val deletedCount = plugin.modelService.deleteRemovedModels()
            
            if (deletedCount > 0) {
                sendMessageWithSound(sender, "§a${prefix} ${deletedCount}개 모델 삭제됨")
            }
            
            // 2단계: 모델 재처리
            sendMessageWithSound(sender, "§7${prefix} 모델 재처리 중...")
            val createdBoneFiles = plugin.modelService.processAllModels()
            
            if (createdBoneFiles > 0) {
                sendMessageWithSound(sender, "§a${prefix} ${createdBoneFiles}개 Bone JSON 파일 생성됨")
            } else {
                sendMessageWithSound(sender, "§e${prefix} 처리할 모델이 없습니다")
            }
            
            // 3단계: 커스텀 모델 데이터 재생성
            if (deletedCount > 0 || createdBoneFiles > 0) {
                // 파일 시스템 동기화를 위한 대기
                Thread.sleep(200)
                
                sendMessageWithSound(sender, "§7${prefix} 커스텀 모델 데이터 재생성 중...")
                val boneCustomModelDataMap = plugin.resourcePackManager.registerCustomModelData()
                sendMessageWithSound(sender, "§a${prefix} 커스텀 모델 데이터 등록 완료: ${boneCustomModelDataMap.size}개 항목")
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val seconds = duration / 1000.0
            sendMessageWithSound(sender, "§a${prefix} 리로드 완료! (총 ${String.format("%.2f", seconds)}초 소요)")
            
            true
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val seconds = duration / 1000.0
            sendMessageWithSound(sender, "§c${prefix} 리로드 중 오류 발생: ${e.message} (${String.format("%.2f", seconds)}초 경과)")
            e.printStackTrace()
            
            false
        }
    }
}

