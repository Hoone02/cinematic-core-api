package org.example.hoon.cinematicCore.util.block

/**
 * 지진 효과 타입
 */
enum class EarthquakeType {
    /** 고르게 회전 (기본값) */
    UNIFORM,
    
    /** 거리 기반 회전 (중심에서 멀어질수록 더 강하게 기울어짐) */
    DISTANCE_BASED,
    
    /** 거리 기반 회전 + 중심 향하기 (중심에서 멀어질수록 더 강하게 기울어지고 중심을 향함) */
    DISTANCE_BASED_CENTER,
    
    /** 넓이 기반 회전 (전방 사각형의 넓이 부분에서 중심에서 멀어질수록 더 강하게 기울어짐) */
    WIDTH_BASED,
    
    /** 넓이 기반 회전 + 중심 향하기 (전방 사각형의 넓이 부분에서 중심에서 멀어질수록 더 강하게 기울어지고 중심을 향함) */
    WIDTH_BASED_CENTER
}

