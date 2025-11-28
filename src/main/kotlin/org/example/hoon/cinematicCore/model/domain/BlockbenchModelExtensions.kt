package org.example.hoon.cinematicCore.model.domain

import org.example.hoon.cinematicCore.util.ConsoleLogger

fun ModelElement.printInfo() {
    ConsoleLogger.info("""
        Element: $name
        UUID: $uuid
        Position: from(${from.x}, ${from.y}, ${from.z}) to(${to.x}, ${to.y}, ${to.z})
        Rotation: (${rotation.x}, ${rotation.y}, ${rotation.z})
        Pivot: (${origin.x}, ${origin.y}, ${origin.z})
    """.trimIndent())
}

fun Bone.printInfo() {
    ConsoleLogger.info("""
        Bone: $name
        UUID: $uuid
        Pivot: (${origin.x}, ${origin.y}, ${origin.z})
        Rotation: (${rotation.x}, ${rotation.y}, ${rotation.z})
        Children: ${children.joinToString(", ")}
    """.trimIndent())
}

fun BlockbenchModel.getElement(name: String): ModelElement? {
    return elements.find { it.name == name }
}

fun BlockbenchModel.getElementByUuid(uuid: String): ModelElement? {
    return elements.find { it.uuid == uuid }
}

fun BlockbenchModel.getBone(name: String): Bone? {
    return bones.find { it.name == name }
}

fun BlockbenchModel.getBoneByUuid(uuid: String): Bone? {
    return bones.find { it.uuid == uuid }
}

fun BlockbenchModel.getChildElements(bone: Bone): List<ModelElement> {
    return bone.children.mapNotNull { childUuid ->
        getElementByUuid(childUuid)
    }
}

fun BlockbenchModel.getChildElements(boneName: String): List<ModelElement>? {
    val bone = getBone(boneName) ?: return null
    return getChildElements(bone)
}

/**
 * bone의 parent bone을 찾음
 * parent UUID를 직접 사용하거나, children에 포함된 경우를 찾음
 */
fun BlockbenchModel.getParentBone(bone: Bone): Bone? {
    // 먼저 parent UUID로 찾기
    if (bone.parent != null) {
        return getBoneByUuid(bone.parent)
    }
    // fallback: children에 포함된 경우 찾기
    return bones.find { parentBone ->
        parentBone.children.contains(bone.uuid)
    }
}

/**
 * bone의 모든 parent bone들을 루트부터 순서대로 반환
 */
fun BlockbenchModel.getParentBoneChain(bone: Bone): List<Bone> {
    val chain = mutableListOf<Bone>()
    var currentBone: Bone? = bone
    var visited = mutableSetOf<String>() // 순환 참조 방지
    
    while (currentBone != null) {
        val parent = getParentBone(currentBone)
        if (parent == null) {
            break
        }
        
        // 순환 참조 방지
        if (visited.contains(parent.uuid)) {
            break
        }
        visited.add(parent.uuid)
        
        chain.add(0, parent) // 루트부터 순서대로 추가
        currentBone = parent
    }
    
    return chain
}

/**
 * bone의 자식 bone들을 반환
 */
fun BlockbenchModel.getChildBones(bone: Bone): List<Bone> {
    return bone.children.mapNotNull { childUuid ->
        getBoneByUuid(childUuid)
    }
}

/**
 * bone 이름에서 태그와 실제 이름을 파싱
 * 형식: "tag-boneName" 또는 "tag1-tag2-boneName"
 * 태그는 맨 앞에 `-`로 구분되어 있어야 함
 * 
 * @return Pair<태그 리스트, 실제 bone 이름>
 */
fun parseBoneNameWithTags(boneName: String): Pair<List<String>, String> {
    if (boneName.isEmpty()) {
        return Pair(emptyList(), "")
    }
    
    // 첫 번째 `-`의 위치를 찾음
    val firstDashIndex = boneName.indexOf('-')
    
    // `-`가 없거나 맨 앞에 있으면 태그 없음
    if (firstDashIndex <= 0) {
        return Pair(emptyList(), boneName)
    }
    
    // 맨 앞부터 첫 번째 `-` 전까지가 태그 부분
    val tagPart = boneName.substring(0, firstDashIndex)
    val actualName = boneName.substring(firstDashIndex + 1)
    
    // 태그가 여러 개일 수 있으므로 `-`로 분리
    val tags = if (tagPart.contains('-')) {
        tagPart.split('-').filter { it.isNotEmpty() }
    } else {
        listOf(tagPart)
    }
    
    return Pair(tags, actualName)
}

/**
 * Bone의 태그 리스트를 반환
 * bone 이름이 "h-head" 형식이면 ["h"]를 반환
 */
fun Bone.getTags(): List<String> {
    return parseBoneNameWithTags(this.name).first
}

/**
 * Bone의 실제 이름을 반환 (태그 제거)
 * bone 이름이 "h-head" 형식이면 "head"를 반환
 */
fun Bone.getActualName(): String {
    return parseBoneNameWithTags(this.name).second
}

/**
 * 특정 태그를 가진 모든 bone들을 반환
 */
fun BlockbenchModel.getBonesByTag(tag: String): List<Bone> {
    return bones.filter { bone ->
        bone.getTags().contains(tag)
    }
}

/**
 * 실제 이름으로 bone을 찾음 (태그 무시)
 * 예: "h-head"와 "head" 모두 "head"로 찾을 수 있음
 */
fun BlockbenchModel.getBoneByActualName(actualName: String): Bone? {
    return bones.find { bone ->
        bone.getActualName() == actualName
    }
}

/**
 * 실제 이름으로 bone을 찾음 (태그 무시)
 * 여러 개가 있을 수 있으므로 리스트로 반환
 */
fun BlockbenchModel.getBonesByActualName(actualName: String): List<Bone> {
    return bones.filter { bone ->
        bone.getActualName() == actualName
    }
}

