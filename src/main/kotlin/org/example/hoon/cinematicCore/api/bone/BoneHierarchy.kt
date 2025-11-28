package org.example.hoon.cinematicCore.api.bone

import org.example.hoon.cinematicCore.model.domain.Bone

/**
 * Bone의 계층 구조 정보를 담는 데이터 클래스
 * 
 * @property bone 현재 Bone
 * @property parent 부모 Bone (없으면 null)
 * @property parents 루트부터 현재까지의 모든 부모 Bone 리스트 (순서대로)
 * @property children 자식 Bone 리스트
 */
data class BoneHierarchy(
    val bone: Bone,
    val parent: Bone?,
    val parents: List<Bone>,
    val children: List<Bone>
)






