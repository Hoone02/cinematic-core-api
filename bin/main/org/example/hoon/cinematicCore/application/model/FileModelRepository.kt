package org.example.hoon.cinematicCore.application.model

import org.example.hoon.cinematicCore.core.model.ModelRepository
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.loader.ModelFileLoader
import java.io.File

/**
 * 파일 기반 모델 저장소 구현
 */
class FileModelRepository(
    private val modelsDirectory: File,
    private val loader: ModelFileLoader = ModelFileLoader(modelsDirectory)
) : ModelRepository {
    
    override fun loadAll(): List<BlockbenchModel> {
        return loader.loadAllModels()
    }
    
    override fun loadByName(modelName: String): BlockbenchModel? {
        return loader.loadModelByName(modelName)
    }
}

