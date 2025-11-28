package org.example.hoon.cinematicCore.model.domain

/**
 * Blockbench UV 포인트 정보
 * 가장자리 면을 기준으로 저장:
 * - leftEdge: 가장 왼쪽면의 위치 (horizontal)
 * - topEdge: 윗쪽면의 위치 (vertical)
 * - rightEdge: 가장 오른쪽면의 위치 (horizontal)
 * - bottomEdge: 가장 아랫면의 위치 (vertical)
 */
data class UVPoint(
    val leftEdge: Double,      // 첫번째: 가장 왼쪽면의 위치
    val topEdge: Double,       // 두번째: 윗쪽면의 위치
    val rightEdge: Double,      // 세번째: 가장 오른쪽면의 위치
    val bottomEdge: Double,    // 네번째: 가장 아랫면의 위치
    val textureId: String? = null  // Blockbench에서 사용하는 텍스처 ID
)

