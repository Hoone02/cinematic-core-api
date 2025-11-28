package org.example.hoon.cinematicCore.cutscene

/**
 * 컷씬 데이터 클래스
 * 
 * @param name 컷씬 이름
 * @param keyframes 키프레임 리스트 (index 순서대로 정렬되어 있어야 함)
 */
data class Cutscene(
    val name: String,
    val keyframes: List<Keyframe>  // index 순서대로 정렬
)

