package org.example.hoon.cinematicCore

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.example.hoon.cinematicCore.adapter.bukkit.ServiceContainer
import org.example.hoon.cinematicCore.command.Command
import org.example.hoon.cinematicCore.command.TestCommand
import org.example.hoon.cinematicCore.cutscene.CutsceneManager
import org.example.hoon.cinematicCore.cutscene.EditModeManager
import org.example.hoon.cinematicCore.cutscene.KeyframeDisplayManager
import org.example.hoon.cinematicCore.cutscene.KeyframeInteractionListener
import org.example.hoon.cinematicCore.cutscene.KeyframeRelocationManager
import org.example.hoon.cinematicCore.cutscene.KeyframeTimingInputManager
import org.example.hoon.cinematicCore.listener.BoneParticleListener
import org.example.hoon.cinematicCore.listener.HitboxListener
import org.example.hoon.cinematicCore.listener.DisguiseSyncListener
import org.example.hoon.cinematicCore.listener.ModelAnimationListener
import org.example.hoon.cinematicCore.model.fs.ModelFilesystem
import org.example.hoon.cinematicCore.model.service.EntityModelPersistenceService
import org.example.hoon.cinematicCore.model.service.ModelService
import org.example.hoon.cinematicCore.resourcepack.ResourcePackManager
import org.example.hoon.cinematicCore.service.ReloadService
import org.example.hoon.cinematicCore.util.ConsoleLogger
import org.example.hoon.cinematicCore.util.prefix

/**
 * CinematicCore 플러그인 메인 클래스
 * 플러그인 초기화 및 의존성 관리만 담당
 */
class CinematicCore : JavaPlugin() {

    companion object {
        lateinit var instance: CinematicCore
            private set
    }

    // 서비스 컨테이너 (새로운 구조)
    lateinit var services: ServiceContainer
        private set
    
    // 기존 호환성 유지
    lateinit var modelFilesystem: ModelFilesystem
        private set
    lateinit var resourcePackManager: ResourcePackManager
        private set
    lateinit var modelService: ModelService
        private set
    
    // 컷씬 시스템 인스턴스 (재사용을 위해 저장)
    lateinit var cutsceneManager: org.example.hoon.cinematicCore.cutscene.CutsceneManager
        private set
    lateinit var editModeManager: org.example.hoon.cinematicCore.cutscene.EditModeManager
        private set
    lateinit var keyframeDisplayManager: org.example.hoon.cinematicCore.cutscene.KeyframeDisplayManager
        private set
    lateinit var pathVisualizer: org.example.hoon.cinematicCore.cutscene.PathVisualizer
        private set
    lateinit var cutscenePlayer: org.example.hoon.cinematicCore.cutscene.CutscenePlayer
        private set
    lateinit var keyframeTimingInputManager: KeyframeTimingInputManager
        private set
    lateinit var cutsceneCommand: org.example.hoon.cinematicCore.command.CutsceneCommand
        private set
    
    private lateinit var entityModelPersistenceService: EntityModelPersistenceService

    override fun onEnable() {
        instance = this
        println("이거 맞음?")
        // 서비스 컨테이너 초기화
        services = ServiceContainer(this)
        services.initialize()

        // 기존 호환성 유지 (기존 코드가 여전히 작동하도록)
        modelFilesystem = services.filesystem
        modelService = services.modelService
        
        // 엔티티 모델 저장/로드 서비스 초기화
        entityModelPersistenceService = EntityModelPersistenceService(this)

        // 1단계: 리소스팩 기본 구조 생성 (폴더, pack.mcmeta, blocks.json)
        resourcePackManager = ResourcePackManager(this)
        resourcePackManager.initialize()

        Bukkit.getPluginManager().registerEvents(ModelAnimationListener(), this)
        Bukkit.getPluginManager().registerEvents(HitboxListener(), this)
        Bukkit.getPluginManager().registerEvents(DisguiseSyncListener(this), this)
        //Bukkit.getPluginManager().registerEvents(BoneParticleListener(), this)
        
        // 컷씬 시스템 이벤트 리스너 등록
        cutsceneManager = CutsceneManager(this)
        editModeManager = EditModeManager(this)
        keyframeDisplayManager = KeyframeDisplayManager(this)
        pathVisualizer = org.example.hoon.cinematicCore.cutscene.PathVisualizer(this)
        cutscenePlayer = org.example.hoon.cinematicCore.cutscene.CutscenePlayer(this)
        keyframeTimingInputManager = KeyframeTimingInputManager(this, cutsceneManager, pathVisualizer)
        val keyframePauseInputManager = org.example.hoon.cinematicCore.cutscene.KeyframePauseInputManager(this, cutsceneManager, pathVisualizer)
        val keyframeSmoothInputManager = org.example.hoon.cinematicCore.cutscene.KeyframeSmoothInputManager(this, cutsceneManager, pathVisualizer)
        val keyframeRelocationManager = KeyframeRelocationManager(
            editModeManager,
            keyframeDisplayManager,
            pathVisualizer,
            cutsceneManager
        )
        Bukkit.getPluginManager().registerEvents(keyframeTimingInputManager, this)
        Bukkit.getPluginManager().registerEvents(keyframePauseInputManager, this)
        
        // CutsceneCommand 생성
        cutsceneCommand = org.example.hoon.cinematicCore.command.CutsceneCommand(
            cutsceneManager,
            editModeManager,
            keyframeDisplayManager,
            pathVisualizer,
            cutscenePlayer
        )
        
        val keyframeInteractionListener = KeyframeInteractionListener(
            this,
            keyframeDisplayManager,
            cutsceneManager,
            editModeManager,
            pathVisualizer,
            keyframeRelocationManager,
            keyframeTimingInputManager,
            keyframePauseInputManager,
            keyframeSmoothInputManager,
            cutscenePlayer
        )
        Bukkit.getPluginManager().registerEvents(keyframeInteractionListener, this)
        
        val cutsceneEditHandler = org.example.hoon.cinematicCore.cutscene.CutsceneEditHandler(
            this,
            editModeManager,
            cutsceneManager,
            keyframeDisplayManager,
            pathVisualizer,
            cutscenePlayer,
            keyframeRelocationManager,
            keyframeTimingInputManager,
            keyframePauseInputManager
        )
        Bukkit.getPluginManager().registerEvents(cutsceneEditHandler, this)
        
        val cutsceneCleanupListener = org.example.hoon.cinematicCore.cutscene.CutsceneCleanupListener(
            editModeManager,
            keyframeDisplayManager,
            pathVisualizer,
            keyframeRelocationManager,
            keyframeTimingInputManager,
            keyframePauseInputManager
        )
        Bukkit.getPluginManager().registerEvents(cutsceneCleanupListener, this)

        // 리로드 서비스 초기화
        val reloadService = ReloadService(this)

        // 명령어 등록
        val mainCommand = getCommand("cinematiccore")
        mainCommand?.setExecutor(Command(reloadService, cutsceneCommand))
        mainCommand?.tabCompleter = Command(reloadService, cutsceneCommand)
        
        getCommand("test")?.setExecutor(TestCommand())

        // 2단계: 삭제된 모델 정리 (models 폴더에 없는 모델 삭제)
        val deletedCount = modelService.deleteRemovedModels()
        
        // 3단계: bbmodel 파싱 및 JSON 생성
        val createdBoneFiles = modelService.processAllModels()
        
        // 4단계: 커스텀 모델 데이터 등록 (leather_horse_armor.json 생성)
        // 삭제되었거나 새로 생성된 경우 모두 재생성 필요
        if (deletedCount > 0 || createdBoneFiles > 0) {
            // 파일 시스템 동기화를 위한 추가 대기
            Thread.sleep(200)
            
            val boneCustomModelDataMap = resourcePackManager.registerCustomModelData()
            ConsoleLogger.success("${prefix} 커스텀 모델 데이터 등록 완료: ${boneCustomModelDataMap.size}개 항목")
        } else {
            ConsoleLogger.warn("${prefix} 생성된 Bone 파일이 없어 커스텀 모델 데이터 등록 스킵")
        }
        
        // 저장된 엔티티 모델 재적용
        val savedCount = entityModelPersistenceService.loadAndRestoreModels()
        if (savedCount > 0) {
            ConsoleLogger.success("${prefix} ${savedCount}개 엔티티 모델 정보 로드 완료")
        }
    }

    override fun onDisable() {
        // 모든 엔티티 모델 정보 저장 및 제거
        val savedCount = entityModelPersistenceService.saveAndRemoveAllModels()
        if (savedCount > 0) {
            ConsoleLogger.success("${prefix} ${savedCount}개 엔티티 모델 정보 저장 및 제거 완료")
        }
    }
}
