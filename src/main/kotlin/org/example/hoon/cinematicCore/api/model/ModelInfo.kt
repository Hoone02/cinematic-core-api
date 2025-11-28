package org.example.hoon.cinematicCore.api.model

import org.example.hoon.cinematicCore.model.domain.BlockbenchModel
import org.example.hoon.cinematicCore.model.service.display.DisplaySession

/**
 * 엔티티에 적용된 모델 정보를 담는 데이터 클래스
 * 
 * @property session DisplaySession - 모델 세션 정보
 * @property model BlockbenchModel - 적용된 모델
 */
data class ModelInfo(
    val session: DisplaySession,
    val model: BlockbenchModel
)






