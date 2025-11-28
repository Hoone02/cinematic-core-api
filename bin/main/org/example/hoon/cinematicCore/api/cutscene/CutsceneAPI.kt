package org.example.hoon.cinematicCore.api.cutscene

import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.CinematicCore
import org.example.hoon.cinematicCore.cutscene.Cutscene
import org.example.hoon.cinematicCore.cutscene.CutsceneManager
import org.example.hoon.cinematicCore.cutscene.CutscenePlayer

/**
 * 컷씬 관련 API 클래스
 */
object CutsceneAPI {
    private val cutsceneManager = CutsceneManager(CinematicCore.instance)
    private val cutscenePlayer: CutscenePlayer
        get() = CinematicCore.instance.cutscenePlayer
    
    /**
     * 컷씬 재생
     * 
     * @param sceneName 컷씬 이름
     * @param player 플레이어
     * @return 재생 성공 여부
     */
    fun play(sceneName: String, player: Player): Boolean {
        val cutscene = cutsceneManager.load(sceneName) ?: return false
        return cutscenePlayer.play(cutscene, player)
    }
    
    /**
     * 컷씬 생성
     * 
     * @param sceneName 컷씬 이름
     * @param player 플레이어
     * @return 생성 성공 여부
     */
    fun create(sceneName: String, player: Player): Boolean {
        // 빈 컷씬 생성
        val cutscene = Cutscene(sceneName, emptyList())
        return cutsceneManager.save(cutscene)
    }
    
    /**
     * 컷씬 로드
     * 
     * @param sceneName 컷씬 이름
     * @return 로드된 컷씬, 없으면 null
     */
    fun load(sceneName: String): Cutscene? {
        return cutsceneManager.load(sceneName)
    }
    
    /**
     * 컷씬 저장
     * 
     * @param cutscene 저장할 컷씬
     * @return 저장 성공 여부
     */
    fun save(cutscene: Cutscene): Boolean {
        return cutsceneManager.save(cutscene)
    }
    
    /**
     * 컷씬 삭제
     * 
     * @param sceneName 컷씬 이름
     * @return 삭제 성공 여부
     */
    fun delete(sceneName: String): Boolean {
        return cutsceneManager.delete(sceneName)
    }
    
    /**
     * 컷씬 목록 반환
     * 
     * @return 컷씬 이름 리스트
     */
    fun list(): List<String> {
        return cutsceneManager.list()
    }
    
    /**
     * 재생 중인 컷씬 강제 종료
     * 
     * @param player 플레이어
     * @param restoreOriginalLocation true면 재생 전 위치로 복원, false면 현재 위치에서 멈춤 (기본값: true)
     * @return 종료 성공 여부 (재생 중이었으면 true, 아니면 false)
     */
    fun stop(player: Player, restoreOriginalLocation: Boolean = true): Boolean {
        if (!cutscenePlayer.playingPlayers.containsKey(player)) {
            return false
        }
        cutscenePlayer.stop(player, restoreOriginalLocation)
        return true
    }
    
    /**
     * 플레이어가 컷씬을 재생 중인지 확인
     * 
     * @param player 플레이어
     * @return 재생 중이면 true
     */
    fun isPlaying(player: Player): Boolean {
        return cutscenePlayer.playingPlayers.containsKey(player)
    }
}

