package org.example.hoon.cinematicCore.core.model

import org.example.hoon.cinematicCore.model.domain.BlockbenchModel

/**
 * 모델 처리 인터페이스
 * 모델 파싱 및 변환을 담당하는 코어 인터페이스
 */
interface ModelProcessor {
    /**
     * 모델을 처리 (텍스처 저장, JSON 생성 등)
     * @param model 처리할 모델
     */
    fun process(model: BlockbenchModel)
    
    /**
     * 캐시 초기화
     */
    fun clearCaches()
}

