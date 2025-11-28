package org.example.hoon.cinematicCore.core.model

import org.example.hoon.cinematicCore.model.domain.BlockbenchModel

/**
 * 모델 저장소 인터페이스
 * 모델 로딩 및 조회를 담당하는 코어 인터페이스
 */
interface ModelRepository {
    /**
     * 모든 모델을 로드
     * @return 로드된 모델 리스트
     */
    fun loadAll(): List<BlockbenchModel>
    
    /**
     * 특정 이름의 모델을 로드
     * @param modelName 모델 이름
     * @return 로드된 모델, 없으면 null
     */
    fun loadByName(modelName: String): BlockbenchModel?
}

