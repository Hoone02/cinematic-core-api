package org.example.hoon.cinematicCore.application.model

import org.example.hoon.cinematicCore.core.model.ModelProcessor
import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.processor.ModelProcessor as InternalModelProcessor

/**
 * 기본 모델 프로세서 구현
 */
class DefaultModelProcessor(
    private val internalProcessor: InternalModelProcessor
) : ModelProcessor {
    
    override fun process(model: BlockbenchModel) {
        internalProcessor.process(model)
    }
    
    override fun clearCaches() {
        internalProcessor.clearCaches()
    }
}

