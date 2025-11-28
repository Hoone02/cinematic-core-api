package org.example.hoon.cinematicCore.model.domain

data class Bone(
    val name: String,
    val uuid: String,
    val origin: Vec3,           // 피벗
    val rotation: Vec3,         // 회전 정보
    val children: List<String>, // 자식 큐브들의 UUID 리스트
    val parent: String? = null,  // 부모 bone의 UUID (null이면 루트 bone)
    val visibility: Boolean = true // 표시 여부 (기본값: true)
)