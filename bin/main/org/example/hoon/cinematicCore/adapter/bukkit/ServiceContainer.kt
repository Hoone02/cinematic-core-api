package org.example.hoon.cinematicCore.adapter.bukkit

import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.application.model.DefaultModelProcessor
import org.example.hoon.cinematicCore.application.model.FileModelRepository
import org.example.hoon.cinematicCore.core.util.Logger
import org.example.hoon.cinematicCore.model.fs.ModelFilesystem
import org.example.hoon.cinematicCore.model.processor.ModelProcessor as InternalModelProcessor
import org.example.hoon.cinematicCore.model.service.ModelService
import org.example.hoon.cinematicCore.model.store.ScaledBoneCache
import java.io.File

/**
 * 서비스 컨테이너
 * 의존성 주입 및 서비스 생성을 담당
 */
class ServiceContainer(
    private val plugin: Plugin
) {
    // 코어 서비스
    val logger: Logger = BukkitLogger()
    
    // 파일 시스템
    val filesystem: ModelFilesystem by lazy {
        ModelFilesystem(plugin)
    }
    
    // 모델 저장소
    val modelRepository: FileModelRepository by lazy {
        val modelsDir = filesystem.ensureModelsDirectory()
        FileModelRepository(modelsDir)
    }
    
    // 스케일된 Bone 캐시
    val scaledBoneCache: ScaledBoneCache by lazy {
        ScaledBoneCache()
    }
    
    // 모델 프로세서
    val modelProcessor: DefaultModelProcessor by lazy {
        val internalProcessor = InternalModelProcessor(filesystem, scaledBoneCache)
        DefaultModelProcessor(internalProcessor)
    }
    
    // 모델 서비스 (기존 호환성 유지)
    val modelService: ModelService by lazy {
        ModelService(plugin, filesystem)
    }
    
    /**
     * 초기화
     */
    fun initialize() {
        modelService.initialize()
    }
}

