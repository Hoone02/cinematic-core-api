package org.example.hoon.cinematicCore.util.block

/**
 * 블럭 감지 유형
 */
enum class DetectionType {
    /** 원형 (반지름 기반) */
    CIRCULAR,
    
    /** 플레이어가 바라보는 방향으로 전방 사각형 (넓이 x 길이) */
    FORWARD_RECTANGLE,
    
    /** 원형 가장자리 (원형인데 가장자리 n범위만큼) */
    CIRCULAR_EDGE,
    
    /** 직접 로케이션 지정 */
    CUSTOM_LOCATIONS
}

