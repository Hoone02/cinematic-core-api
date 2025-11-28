package org.example.hoon.cinematicCore.util.block

/**
 * 원형 감지 설정
 */
data class CircularConfig(
    val radius: Int
)

/**
 * 전방 사각형 감지 설정
 */
data class ForwardRectangleConfig(
    val width: Int,  // 넓이 (좌우)
    val length: Int   // 길이 (전방)
)

/**
 * 원형 가장자리 감지 설정
 */
data class CircularEdgeConfig(
    val radius: Int,
    val edgeRange: Int  // 가장자리 범위
)

