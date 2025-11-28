# CinematicCore API 사용 가이드

## 목차
1. [개요](#개요)
2. [시작하기](#시작하기)
3. [모델 API](#모델-api)
4. [애니메이션 API](#애니메이션-api)
5. [Bone API](#bone-api)
6. [Earthquake API](#earthquake-api)
7. [컷씬 API](#컷씬-api)
8. [전체 예제](#전체-예제)

---

## 개요

CinematicCore API는 Minecraft 플러그인에서 3D 모델, 애니메이션, Bone을 쉽게 제어할 수 있도록 제공하는 통합 API입니다.

### 주요 기능
- **모델 관리**: 엔티티에 모델 적용 및 조회
- **애니메이션 제어**: 애니메이션 재생, 정지, 일시정지
- **Bone 조작**: Bone 위치, 태그, 계층 구조 관리

### API 구조
```kotlin
CinematicCoreAPI
├── Model      // 모델 관련 기능
├── Animation  // 애니메이션 관련 기능
├── Bone      // Bone 관련 기능
└── Cutscene  // 컷씬 관련 기능
```

---

## 시작하기

### 기본 사용법

```kotlin
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

// 메인 API를 통한 접근
CinematicCoreAPI.Model.applyModel(entity, "player_battle", location)
CinematicCoreAPI.Animation.playAnimationByName(entity, "walk")
CinematicCoreAPI.Bone.getBoneLocation(entity, "head")
```

### 개별 API 사용

```kotlin
import org.example.hoon.cinematicCore.api.model.ModelAPI
import org.example.hoon.cinematicCore.api.animation.AnimationAPI
import org.example.hoon.cinematicCore.api.bone.BoneAPI

// 개별 API 사용
ModelAPI.getModel(entity)
AnimationAPI.playAnimation(entity, animation)
BoneAPI.mountEntityOnBone(entity, "head", player)
```

---

## 모델 API

### 1. 엔티티에 모델 적용

엔티티에 Blockbench 모델을 적용합니다.

```kotlin
val entity: LivingEntity = // 엔티티 (예: Silverfish, Player 등)
val location: Location = // 모델을 소환할 위치
val modelName: String = "player_battle" // 모델 파일명 (확장자 제외)

// 모델 적용 (기본값: 데미지 색상 변경 활성화)
val success = CinematicCoreAPI.Model.applyModel(entity, modelName, location)
if (success) {
    println("모델 적용 성공!")
} else {
    println("모델 적용 실패")
}

// 데미지 색상 변경 비활성화하여 모델 적용
val success2 = CinematicCoreAPI.Model.applyModel(
    entity = entity,
    modelName = modelName,
    location = location,
    enableDamageColorChange = false  // 데미지 받을 때 빨간색으로 변하지 않음
)
```

**매개변수:**
- `entity`: 모델을 적용할 LivingEntity
- `modelName`: 적용할 모델 이름 (파일명, 확장자 포함 또는 제외 가능)
- `location`: 모델을 소환할 위치 (null이면 엔티티의 현재 위치 사용)
- `enableDamageColorChange`: 데미지 받을 때 빨간색으로 변하는 기능 활성화 여부 (기본값: `true`)

**반환값:** `Boolean` - 성공 여부

**참고:** `enableDamageColorChange`가 `true`이면 엔티티가 데미지를 받을 때 모델의 색상이 빨간색으로 변경되었다가 0.4초 후 원래 색상으로 복원됩니다. `false`로 설정하면 데미지를 받아도 색상이 변경되지 않습니다.

---

### 2. 엔티티에 적용된 모델 불러오기

엔티티에 적용된 모델 정보를 가져옵니다.

```kotlin
val entity: Entity = // 엔티티

val model = CinematicCoreAPI.Model.getModel(entity)
model?.let {
    println("모델 이름: ${it.modelName}")
    println("Bone 개수: ${it.bones.size}")
    println("애니메이션 개수: ${it.animations.size}")
    println("텍스처 개수: ${it.textures.size}")
}
```

**반환값:** `BlockbenchModel?` - 적용된 모델, 없으면 null

---

### 3. 특정 bone의 모델 가져오기

특정 bone에 적용된 모델을 가져옵니다. bone이 교체된 경우 교체된 모델을 반환합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "head"

val boneModel = CinematicCoreAPI.Model.getBoneModel(entity, boneName)
boneModel?.let {
    println("Bone 모델 이름: ${it.modelName}")
    println("Bone이 교체되었는지 확인 가능")
}
```

**반환값:** `BlockbenchModel?` - bone에 적용된 모델, 없으면 null

---

### 4. 특정 bone의 baseEntity 가져오기

특정 bone의 baseEntity를 가져옵니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "head"

val baseEntity = CinematicCoreAPI.Model.getBoneBaseEntity(entity, boneName)
baseEntity?.let {
    println("Base Entity 타입: ${it.type}")
    println("Base Entity 위치: ${it.location}")
}
```

**반환값:** `LivingEntity?` - baseEntity, 없으면 null

---

### 5. 특정 모델이 적용된 엔티티 불러오기

특정 모델이 적용된 모든 엔티티를 찾습니다.

```kotlin
val modelName: String = "player_battle"
val world: World? = null // null이면 모든 월드에서 검색

val entities = CinematicCoreAPI.Model.getEntitiesWithModel(modelName, world)
println("${modelName} 모델이 적용된 엔티티: ${entities.size}개")

entities.forEach { entity ->
    println("- ${entity.type} at ${entity.location}")
}
```

**매개변수:**
- `modelName`: 찾을 모델 이름
- `world`: 특정 월드에서만 찾을 경우 지정 (null이면 모든 월드)

**반환값:** `List<Entity>` - 해당 모델이 적용된 엔티티 리스트

---

### 6. 모델 적용 여부 확인

엔티티에 모델이 적용되어 있는지 확인합니다.

```kotlin
val entity: Entity = // 엔티티

if (CinematicCoreAPI.Model.hasModel(entity)) {
    println("모델이 적용되어 있습니다")
} else {
    println("모델이 적용되어 있지 않습니다")
}
```

**반환값:** `Boolean` - 모델 적용 여부

---

### 7. 모델 정보 가져오기

엔티티에 적용된 모델의 전체 정보를 가져옵니다.

```kotlin
val entity: Entity = // 엔티티

val modelInfo = CinematicCoreAPI.Model.getModelInfo(entity)
modelInfo?.let {
    println("모델: ${it.model.modelName}")
    println("Base Entity: ${it.session.baseEntity.type}")
    println("Animation Controller: ${it.session.animationController}")
}
```

**반환값:** `ModelInfo?` - 모델 정보 (session, model), 없으면 null

---

### 8. 데미지 색상 변경 기능 설정

데미지 받을 때 빨간색으로 변하는 기능을 활성화/비활성화합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

// 데미지 색상 변경 비활성화
val success = CinematicCoreAPI.Model.setDamageColorChangeEnabled(entity, false)
if (success) {
    println("데미지 색상 변경 비활성화됨")
}

// 데미지 색상 변경 활성화
CinematicCoreAPI.Model.setDamageColorChangeEnabled(entity, true)
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `enable`: 활성화 여부 (`true`: 빨간색으로 변함, `false`: 변하지 않음)

**반환값:** `Boolean` - 설정 성공 여부

---

### 9. 데미지 색상 변경 기능 확인

데미지 받을 때 빨간색으로 변하는 기능이 활성화되어 있는지 확인합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

val enabled = CinematicCoreAPI.Model.isDamageColorChangeEnabled(entity)
enabled?.let {
    if (it) {
        println("데미지 색상 변경: 활성화")
    } else {
        println("데미지 색상 변경: 비활성화")
    }
} ?: println("모델이 적용되어 있지 않습니다")
```

**반환값:** `Boolean?` - 활성화 여부 (활성화: `true`, 비활성화: `false`, 모델이 없으면 `null`)

---

## 애니메이션 API

### 1. 애니메이션 재생 (객체로)

BlockbenchAnimation 객체를 사용하여 애니메이션을 재생합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val model = CinematicCoreAPI.Model.getModel(entity)

// 애니메이션 찾기
val animation = model?.animations?.firstOrNull { it.name == "walk" }
animation?.let {
    // 애니메이션 재생 (속도 1.5배, 중단 가능)
    val success = CinematicCoreAPI.Animation.playAnimation(
        entity = entity,
        animation = it,
        speed = 1.5f,
        interruptible = true
    )
    
    if (success) {
        println("애니메이션 재생 시작")
    }
}
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `animation`: 재생할 BlockbenchAnimation 객체
- `speed`: 재생 속도 (1.0f = 정상 속도, 2.0f = 2배 속도)
- `interruptible`: 애니메이션 중단 가능 여부 (true면 다른 애니메이션으로 교체 가능)

**반환값:** `Boolean` - 재생 성공 여부

---

### 2. 애니메이션 재생 (이름으로)

애니메이션 이름으로 직접 재생합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val animationName: String = "walk"

// 애니메이션 재생 (속도 1.5배)
val success = CinematicCoreAPI.Animation.playAnimationByName(
    entity = entity,
    animationName = animationName,
    speed = 1.5f,
    interruptible = true
)

if (success) {
    println("애니메이션 '${animationName}' 재생 시작")
} else {
    println("애니메이션을 찾을 수 없거나 재생 실패")
}
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `animationName`: 재생할 애니메이션 이름
- `speed`: 재생 속도 (기본값: 1.0f)
- `interruptible`: 애니메이션 중단 가능 여부 (기본값: true)

**반환값:** `Boolean` - 재생 성공 여부

---

### 3. 애니메이션 정지

재생 중인 애니메이션을 정지합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

// 애니메이션 정지 (포즈 초기화)
val success = CinematicCoreAPI.Animation.stopAnimation(
    entity = entity,
    resetPose = true
)

if (success) {
    println("애니메이션 정지됨")
}
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `resetPose`: 포즈를 초기 상태로 리셋할지 여부 (기본값: true)

**반환값:** `Boolean` - 정지 성공 여부

---

### 4. 애니메이션 일시정지

재생 중인 애니메이션을 일시정지합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

// 애니메이션 일시정지
val success = CinematicCoreAPI.Animation.pauseAnimation(entity)

if (success) {
    println("애니메이션 일시정지됨")
}
```

**반환값:** `Boolean` - 일시정지 성공 여부

---

### 5. 애니메이션 재개

일시정지된 애니메이션을 재개합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

// 애니메이션 재개
val success = CinematicCoreAPI.Animation.resumeAnimation(entity)

if (success) {
    println("애니메이션 재개됨")
}
```

**반환값:** `Boolean` - 재개 성공 여부

---

### 6. 특정 모델에서 애니메이션 찾기

모델에서 특정 애니메이션을 찾습니다.

```kotlin
val model: BlockbenchModel = // 모델
val animationName: String = "walk"

val animation = CinematicCoreAPI.Animation.findAnimation(model, animationName)
animation?.let {
    println("애니메이션 이름: ${it.name}")
    println("애니메이션 길이: ${it.length}초")
    println("루프 모드: ${it.loopMode}")
} ?: println("애니메이션을 찾을 수 없습니다")
```

**반환값:** `BlockbenchAnimation?` - 찾은 애니메이션, 없으면 null

---

### 7. 현재 재생 중인 애니메이션 가져오기

현재 재생 중인 애니메이션을 가져옵니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

val currentAnimation = CinematicCoreAPI.Animation.getCurrentAnimation(entity)
currentAnimation?.let {
    println("현재 재생 중: ${it.name}")
    println("길이: ${it.length}초")
} ?: println("재생 중인 애니메이션 없음")
```

**반환값:** `BlockbenchAnimation?` - 현재 재생 중인 애니메이션, 없으면 null

---

### 8. 애니메이션 재생 여부 확인

애니메이션이 재생 중인지 확인합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티

if (CinematicCoreAPI.Animation.isAnimationPlaying(entity)) {
    println("애니메이션 재생 중")
} else {
    println("애니메이션 재생 중 아님")
}
```

**반환값:** `Boolean` - 재생 중이면 true

---

## Bone API

### 1. 특정 bone에 엔티티 탑승시키기

특정 bone에 엔티티를 탑승시킵니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "head"
val passenger: Entity = // 탑승시킬 엔티티 (예: Player)

// 오프셋 없이 bone의 pivot 위치에 탑승 (기본값: 내릴 수 있음)
val success1 = CinematicCoreAPI.Bone.mountEntityOnBone(
    entity = entity,
    boneName = boneName,
    passenger = passenger
)

// 오프셋을 지정하여 탑승
import org.joml.Vector3f
val offset = Vector3f(0f, 0.5f, 0f) // Y축으로 0.5 블록 위
val success2 = CinematicCoreAPI.Bone.mountEntityOnBone(
    entity = entity,
    boneName = boneName,
    passenger = passenger,
    offset = offset
)

// 내릴 수 없게 설정하여 탑승 (자동으로 내려가지 않음)
val success3 = CinematicCoreAPI.Bone.mountEntityOnBone(
    entity = entity,
    boneName = boneName,
    passenger = passenger,
    offset = null,
    canDismount = false  // 내릴 수 없음
)

if (success3) {
    println("엔티티가 ${boneName} bone에 탑승했습니다 (내릴 수 없음)")
}
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `boneName`: 탑승시킬 bone의 이름
- `passenger`: 탑승시킬 엔티티
- `offset`: 탑승 위치 오프셋 (null이면 bone의 pivot 위치 사용)
- `canDismount`: 내릴 수 있는지 여부 (기본값: `true`, `false`로 설정하면 자동으로 내려가지 않음)

**반환값:** `Boolean` - 탑승 성공 여부

**참고:** `canDismount = false`로 설정하면 엔티티가 Shift 키를 눌러도 내려가지 않으며, 다른 방법으로도 자동으로 내려가지 않습니다. 강제로 내리려면 `dismountEntityFromBone()` 메서드를 사용하세요.

---

### 2. 특정 bone을 다른 모델의 bone으로 교체하기

특정 bone을 다른 모델의 bone으로 교체합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val targetBoneName: String = "head"
val replacementModelName: String = "player_battle"
val replacementBoneName: String = "head" // 기본값: targetBoneName과 동일

val success = CinematicCoreAPI.Bone.replaceBone(
    entity = entity,
    targetBoneName = targetBoneName,
    replacementModelName = replacementModelName,
    replacementBoneName = replacementBoneName
)

if (success) {
    println("${targetBoneName} bone이 ${replacementModelName}의 ${replacementBoneName}로 교체되었습니다")
} else {
    println("Bone 교체 실패")
}
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `targetBoneName`: 교체할 bone의 이름
- `replacementModelName`: 교체할 모델의 이름
- `replacementBoneName`: 교체할 bone의 이름 (기본값: targetBoneName과 동일)

**반환값:** `Boolean` - 교체 성공 여부

---

### 3. 특정 bone의 위치를 Location으로 받아오기

특정 bone의 pivot 위치를 Location으로 가져옵니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "head"

val location = CinematicCoreAPI.Bone.getBoneLocation(entity, boneName)
location?.let {
    println("${boneName} bone 위치:")
    println("  X: ${it.x}")
    println("  Y: ${it.y}")
    println("  Z: ${it.z}")
    println("  World: ${it.world?.name}")
    
    // 다른 엔티티를 이 위치로 텔레포트
    // otherEntity.teleport(it)
} ?: println("Bone을 찾을 수 없습니다")
```

**반환값:** `Location?` - bone의 pivot 위치, 없으면 null

---

### 4. bone의 태그 받아오기

bone의 태그를 가져옵니다. bone 이름이 "h-head" 형식이면 ["h"]를 반환합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "h-head" // 또는 "head"

val tags = CinematicCoreAPI.Bone.getBoneTags(entity, boneName)
println("${boneName} bone의 태그: ${tags.joinToString(", ")}")

// 특정 태그 확인
if (tags.contains("h")) {
    println("이 bone은 h 태그를 가지고 있습니다 (시야 추적)")
}
```

**반환값:** `List<String>` - bone의 태그 리스트 (태그가 없으면 빈 리스트)

---

### 5. bone의 계층 구조

bone의 계층 구조 정보를 가져옵니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "head"

val hierarchy = CinematicCoreAPI.Bone.getBoneHierarchy(entity, boneName)
hierarchy?.let {
    println("Bone: ${it.bone.name}")
    
    // 부모 bone
    it.parent?.let { parent ->
        println("부모: ${parent.name}")
    } ?: println("부모 없음 (루트 bone)")
    
    // 모든 부모 bone들 (루트부터 순서대로)
    if (it.parents.isNotEmpty()) {
        println("부모 체인: ${it.parents.joinToString(" -> ") { p -> p.name }}")
    }
    
    // 자식 bone들
    if (it.children.isNotEmpty()) {
        println("자식: ${it.children.joinToString(", ") { c -> c.name }}")
    } else {
        println("자식 없음")
    }
} ?: println("Bone을 찾을 수 없습니다")
```

**반환값:** `BoneHierarchy?` - bone의 계층 구조 정보, 없으면 null

**BoneHierarchy 구조:**
- `bone`: 현재 Bone
- `parent`: 부모 Bone (없으면 null)
- `parents`: 루트부터 현재까지의 모든 부모 Bone 리스트
- `children`: 자식 Bone 리스트

---

### 6. bone의 디스플레이 엔티티 불러오기

bone의 ItemDisplay 엔티티를 가져옵니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val boneName: String = "head"

val display = CinematicCoreAPI.Bone.getBoneDisplay(entity, boneName)
display?.let {
    println("Display 엔티티 UUID: ${it.uniqueId}")
    println("Display 위치: ${it.location}")
    println("Display 아이템: ${it.itemStack?.type}")
    
    // Display 엔티티 조작 가능
    // it.isGlowing = true
    // it.isVisible = false
} ?: println("Display 엔티티를 찾을 수 없습니다")
```

**반환값:** `ItemDisplay?` - bone의 ItemDisplay 엔티티, 없으면 null

---

### 7. 탑승한 엔티티 강제로 내리기

탑승한 엔티티를 강제로 내립니다. `canDismount = false`로 설정된 경우에도 강제로 내릴 수 있습니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val passenger: Entity = // 내릴 엔티티

val success = CinematicCoreAPI.Bone.dismountEntityFromBone(entity, passenger)
if (success) {
    println("엔티티를 내렸습니다")
} else {
    println("탑승하지 않았거나 내리기 실패")
}
```

**매개변수:**
- `entity`: 모델이 적용된 엔티티
- `passenger`: 내릴 엔티티

**반환값:** `Boolean` - 내림 성공 여부

---

### 8. 엔티티 탑승 여부 확인

엔티티가 특정 bone에 탑승했는지 확인합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val passenger: Entity = // 확인할 엔티티

if (CinematicCoreAPI.Bone.isEntityMountedOnBone(entity, passenger)) {
    println("엔티티가 탑승 중입니다")
} else {
    println("엔티티가 탑승하지 않았습니다")
}
```

**반환값:** `Boolean` - 탑승 여부

---

### 9. 탑승한 엔티티의 내림 가능 여부 확인

탑승한 엔티티가 내릴 수 있는지 확인합니다.

```kotlin
val entity: Entity = // 모델이 적용된 엔티티
val passenger: Entity = // 확인할 엔티티

val canDismount = CinematicCoreAPI.Bone.canPassengerDismount(entity, passenger)
canDismount?.let {
    if (it) {
        println("내릴 수 있습니다")
    } else {
        println("내릴 수 없습니다 (강제로 내리려면 dismountEntityFromBone 사용)")
    }
} ?: println("탑승하지 않았습니다")
```

**반환값:** `Boolean?` - 내릴 수 있는지 여부 (탑승하지 않았으면 `null`)

---

## Earthquake API

Earthquake API는 블럭에 지진 효과를 적용하는 기능을 제공합니다. 블럭을 베리어로 변경하고 BlockDisplay 엔티티를 소환하여 회전 효과를 주고, 지정된 시간 후 원래 상태로 복원합니다.

### 주요 기능
- **다양한 감지 유형**: 원형, 전방 사각형, 원형 가장자리, 커스텀 위치
- **다양한 지진 효과 타입**: 고르게 회전, 거리 기반 회전, 중심 향하기 등
- **랜덤 사이즈**: 각 BlockDisplay의 사이즈를 랜덤하게 변경 (0.9~1.2 범위)
- **순차적 처리**: 중심에서 가장자리로(또는 그 반대) 파도처럼 퍼져나가는 효과
- **자동 복원**: 지정된 시간 후 랜덤 순서로 블럭 복원
- **애니메이션 복원**: BlockDisplay가 회전값 0으로 부드럽게 복원

---

### 1. 기본 사용법

```kotlin
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.example.hoon.cinematicCore.util.block.Earthquake
import org.example.hoon.cinematicCore.util.block.DetectionType
import org.example.hoon.cinematicCore.util.block.EarthquakeType
import org.example.hoon.cinematicCore.util.block.CircularConfig

val plugin: Plugin = // 플러그인 인스턴스
val player: Player = // 플레이어
val location: Location = // 중심 위치

// Earthquake 인스턴스 생성
val earthquake = Earthquake(plugin)

// 원형 감지로 지진 효과 실행
earthquake.execute(
    centerLocation = location,
    player = player,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.DISTANCE_BASED_CENTER
)
```

---

### 2. 감지 유형 (DetectionType)

#### 2.1. 원형 감지 (CIRCULAR)

중심 위치를 기준으로 반지름 내의 블럭을 감지합니다.

```kotlin
import org.example.hoon.cinematicCore.util.block.CircularConfig

earthquake.execute(
    centerLocation = location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),  // 반지름 10블록
    restoreTimeSeconds = 5
)
```

**CircularConfig 매개변수:**
- `radius`: 감지 반지름 (블록 단위)

---

#### 2.2. 전방 사각형 감지 (FORWARD_RECTANGLE)

플레이어가 바라보는 방향으로 전방 사각형 영역의 블럭을 감지합니다.

```kotlin
import org.example.hoon.cinematicCore.util.block.ForwardRectangleConfig

earthquake.execute(
    player = player,  // 플레이어 필수
    type = DetectionType.FORWARD_RECTANGLE,
    config = ForwardRectangleConfig(
        width = 5,   // 넓이 (좌우)
        length = 10   // 길이 (전방)
    ),
    restoreTimeSeconds = 5
)
```

**ForwardRectangleConfig 매개변수:**
- `width`: 사각형의 넓이 (좌우 방향, 블록 단위)
- `length`: 사각형의 길이 (전방 방향, 블록 단위)

---

#### 2.3. 원형 가장자리 감지 (CIRCULAR_EDGE)

원형 영역 중 가장자리 부분만 감지합니다.

```kotlin
import org.example.hoon.cinematicCore.util.block.CircularEdgeConfig

earthquake.execute(
    centerLocation = location,
    type = DetectionType.CIRCULAR_EDGE,
    config = CircularEdgeConfig(
        radius = 10,      // 전체 반지름
        edgeRange = 3     // 가장자리 범위
    ),
    restoreTimeSeconds = 5
)
```

**CircularEdgeConfig 매개변수:**
- `radius`: 전체 반지름 (블록 단위)
- `edgeRange`: 가장자리 범위 (블록 단위, 반지름에서 이 값만큼 뺀 내부는 제외)

---

#### 2.4. 커스텀 위치 감지 (CUSTOM_LOCATIONS)

직접 지정한 위치들의 블럭을 감지합니다.

```kotlin
val customLocations = listOf(
    location1,
    location2,
    location3
)

earthquake.execute(
    type = DetectionType.CUSTOM_LOCATIONS,
    customLocations = customLocations,
    restoreTimeSeconds = 5
)
```

---

### 3. 지진 효과 타입 (EarthquakeType)

#### 3.1. 고르게 회전 (UNIFORM)

모든 블럭이 동일한 범위로 랜덤 회전합니다. (기본값)

```kotlin
earthquake.execute(
    centerLocation = location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.UNIFORM
)
```

---

#### 3.2. 거리 기반 회전 (DISTANCE_BASED)

중심에서 멀어질수록 더 강하게 기울어집니다. (원형 감지 전용)

```kotlin
earthquake.execute(
    centerLocation = location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.DISTANCE_BASED
)
```

---

#### 3.3. 거리 기반 회전 + 중심 향하기 (DISTANCE_BASED_CENTER)

중심에서 멀어질수록 더 강하게 기울어지고, 블럭이 중심을 향하도록 회전합니다. (원형 감지 전용)

```kotlin
earthquake.execute(
    centerLocation = location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.DISTANCE_BASED_CENTER
)
```

**특징:**
- 중심: -0.0 높이, 가장자리: 0.4 높이로 자동 조정
- 중심 방향으로 pitch와 roll이 기울어짐
- 추가로 작은 랜덤 회전 적용

---

#### 3.4. 넓이 기반 회전 (WIDTH_BASED)

전방 사각형의 넓이 방향에서 중심에서 멀어질수록 더 강하게 기울어집니다. (전방 사각형 감지 전용)

```kotlin
earthquake.execute(
    player = player,
    type = DetectionType.FORWARD_RECTANGLE,
    config = ForwardRectangleConfig(width = 5, length = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.WIDTH_BASED
)
```

---

#### 3.5. 넓이 기반 회전 + 중심 향하기 (WIDTH_BASED_CENTER)

전방 사각형의 넓이 방향에서 중심에서 멀어질수록 더 강하게 기울어지고, 블럭이 중심을 향하도록 회전합니다. (전방 사각형 감지 전용)

```kotlin
earthquake.execute(
    player = player,
    type = DetectionType.FORWARD_RECTANGLE,
    config = ForwardRectangleConfig(width = 5, length = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.WIDTH_BASED_CENTER
)
```

**특징:**
- 중심: -0.0 높이, 가장자리: 0.4 높이로 자동 조정
- 중심 방향으로 pitch와 roll이 기울어짐
- 추가로 작은 랜덤 회전 적용

---

### 4. 순차적 처리 (Sequential Processing)

순차적 처리를 활성화하면 중심에서 가장자리로(또는 그 반대) 파도처럼 퍼져나가는 효과를 만들 수 있습니다.

#### 4.1. 기본 사용법

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    sequential = true,           // 순차적 처리 활성화
    startFromCenter = true,       // 중심부터 시작 (true) 또는 가장자리부터 (false)
    totalSequentialTime = 2.0f    // 순차적 처리 전체 시간 (초)
)
```

**매개변수 설명:**
- `sequential`: 순차적 처리 활성화 여부 (기본값: `false`)
- `startFromCenter`: 시작 위치 (기본값: `true`)
  - `true`: 중심부터 가장자리로 퍼짐
  - `false`: 가장자리부터 중심으로 퍼짐
- `totalSequentialTime`: 순차적 처리 전체 시간 (초, 기본값: `1.0f`)

#### 4.2. 동작 원리

1. **거리별 그룹화**: 블럭을 중심으로부터의 거리(정수)로 그룹화
   - 거리 0-1 블록 → 그룹 0
   - 거리 2-3 블록 → 그룹 1
   - 거리 4-5 블록 → 그룹 2
   - ...

2. **그룹별 순차 처리**:
   - `startFromCenter = true`: 그룹 0 → 그룹 1 → 그룹 2 (중심 → 가장자리)
   - `startFromCenter = false`: 그룹 2 → 그룹 1 → 그룹 0 (가장자리 → 중심)

3. **시간 분배**: 전체 시간(`totalSequentialTime`)을 그룹 수로 나누어 각 그룹에 균등하게 분배하여 처리

#### 4.3. 사용 예제

**예제 1: 중심부터 파도 효과**
```kotlin
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    sequential = true,
    startFromCenter = true,       // 중심부터 시작
    totalSequentialTime = 2.0f    // 2초에 걸쳐 순차적으로 처리
)
```

**예제 2: 가장자리부터 파도 효과**
```kotlin
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    sequential = true,
    startFromCenter = false,      // 가장자리부터 시작
    totalSequentialTime = 3.0f    // 3초에 걸쳐 순차적으로 처리
)
```

**예제 3: 전방 사각형 순차적 처리**
```kotlin
earthquake.execute(
    player = player,
    type = DetectionType.FORWARD_RECTANGLE,
    config = ForwardRectangleConfig(width = 5, length = 10),
    restoreTimeSeconds = 5,
    sequential = true,
    startFromCenter = true,       // 플레이어 위치(중심)부터 시작
    totalSequentialTime = 1.5f    // 1.5초에 걸쳐 순차적으로 처리
)
```

---

### 5. 고급 옵션

#### 5.1. 전체 매개변수

```kotlin
earthquake.execute(
    centerLocation: Location? = null,           // 중심 위치
    player: Player? = null,                     // 플레이어 (전방 사각형 시 필수)
    type: DetectionType,                        // 감지 유형
    config: Any? = null,                        // 감지 설정
    customLocations: List<Location>? = null,   // 커스텀 위치 리스트
    restoreTimeSeconds: Long,                   // 복원 시간 (초)
    scanDepth: Int = 3,                        // 스캔 깊이 (몇 칸 아래까지)
    scanAbove: Int = 3,                        // 위쪽으로 스캔할 높이 (몇 칸 위까지)
    rotationRange: Float = 10f,                // 회전 범위 (-10도 ~ 10도)
    spawnOffsetY: Double = 0.1,                // 스폰 오프셋 Y (기본값)
    animationTicks: Long = 2L,                  // 회전 애니메이션 틱 수
    delayBetweenRestores: Long = 2L,          // 블럭 복원 사이의 틱 간격
    earthquakeType: EarthquakeType = EarthquakeType.UNIFORM,  // 지진 효과 타입
    sequential: Boolean = false,               // 순차적 처리 활성화
    startFromCenter: Boolean = true,            // 중심부터 시작 여부
    totalSequentialTime: Float = 1.0f,         // 순차적 처리 전체 시간 (초)
    randomSize: Boolean = false                // 랜덤 사이즈 활성화 (기본값: false)
)
```

**매개변수 설명:**
- `centerLocation`: 중심 위치 (원형, 원형 가장자리 시 사용)
- `player`: 플레이어 (전방 사각형 시 필수)
- `type`: 감지 유형 (`DetectionType` enum)
- `config`: 감지 설정 객체 (`CircularConfig`, `ForwardRectangleConfig`, `CircularEdgeConfig`)
- `customLocations`: 커스텀 위치 리스트 (`CUSTOM_LOCATIONS` 시 필수)
- `restoreTimeSeconds`: 블럭이 원래 상태로 복원되는 시간 (초)
- `scanDepth`: 블럭 감지 깊이 (기본값: 3, 3칸 아래까지 감지)
- `scanAbove`: 위쪽으로 스캔할 높이 (기본값: 3, 3칸 위까지 감지)
- `rotationRange`: 회전 범위 (기본값: 10도, -10도 ~ 10도)
- `spawnOffsetY`: BlockDisplay 스폰 높이 오프셋 (기본값: 0.1)
- `animationTicks`: 복원 시 회전 애니메이션 틱 수 (기본값: 2)
- `delayBetweenRestores`: 블럭 복원 사이의 틱 간격 (기본값: 2)
- `earthquakeType`: 지진 효과 타입 (`EarthquakeType` enum)
- `sequential`: 순차적 처리 활성화 여부 (기본값: `false`)
- `startFromCenter`: 시작 위치 (기본값: `true`, `true`: 중심부터, `false`: 가장자리부터)
- `totalSequentialTime`: 순차적 처리 전체 시간 (초, 기본값: `1.0f`)
- `randomSize`: 랜덤 사이즈 활성화 여부 (기본값: `false`, `true`일 때 각 BlockDisplay의 사이즈를 랜덤하게 변경)

---

### 5. 사용 예제

#### 예제 1: 기본 원형 지진 효과

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5
)
```

---

#### 예제 2: 중심 향하는 지진 효과

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.DISTANCE_BASED_CENTER
)
```

---

#### 예제 3: 전방 사각형 지진 효과

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    player = player,
    type = DetectionType.FORWARD_RECTANGLE,
    config = ForwardRectangleConfig(width = 5, length = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.WIDTH_BASED_CENTER
)
```

---

#### 예제 4: 커스텀 위치 지진 효과

```kotlin
val customLocations = (1..10).map { i ->
    player.location.clone().add(i.toDouble(), 0.0, 0.0)
}

val earthquake = Earthquake(plugin)
earthquake.execute(
    type = DetectionType.CUSTOM_LOCATIONS,
    customLocations = customLocations,
    restoreTimeSeconds = 3
)
```

---

#### 예제 5: 고급 옵션 사용

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 15),
    restoreTimeSeconds = 10,
    scanDepth = 5,              // 5칸 아래까지 감지
    rotationRange = 15f,         // -15도 ~ 15도 회전
    spawnOffsetY = 0.2,         // 0.2블록 위에서 스폰
    animationTicks = 5L,         // 5틱 동안 애니메이션
    delayBetweenRestores = 3L,   // 3틱 간격으로 복원
    earthquakeType = EarthquakeType.DISTANCE_BASED_CENTER
)
```

---

#### 예제 6: 순차적 처리 (파도 효과)

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    sequential = true,           // 순차적 처리 활성화
    startFromCenter = true,       // 중심부터 시작
    totalSequentialTime = 2.0f,   // 2초에 걸쳐 순차적으로 처리
    earthquakeType = EarthquakeType.DISTANCE_BASED_CENTER
)
```

---

#### 예제 7: 랜덤 사이즈 적용

```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    randomSize = true  // 랜덤 사이즈 활성화 (자세한 내용은 섹션 6 참조)
)
```

---

### 6. 랜덤 사이즈 (Random Size)

랜덤 사이즈 기능을 활성화하면 각 BlockDisplay의 사이즈가 랜덤하게 변경되어 더 자연스러운 지진 효과를 만들 수 있습니다.

#### 6.1. 기본 사용법

```kotlin
earthquake.execute(
    centerLocation = location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    randomSize = true  // 랜덤 사이즈 활성화
)
```

#### 6.2. 사이즈 범위

- **기본 범위**: 0.9 ~ 1.2
- 각 BlockDisplay의 X, Y, Z 축에 동일한 랜덤 값이 적용됩니다
- 예: 0.95, 1.08, 1.15 등의 값이 각 블럭에 무작위로 적용됩니다

#### 6.3. 사용 예제

**예제: 원형 지진 효과 + 랜덤 사이즈**
```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    earthquakeType = EarthquakeType.DISTANCE_BASED_CENTER,
    randomSize = true  // 각 블럭의 사이즈가 랜덤하게 변경됨
)
```

**예제: 순차적 처리 + 랜덤 사이즈**
```kotlin
val earthquake = Earthquake(plugin)
earthquake.execute(
    centerLocation = player.location,
    type = DetectionType.CIRCULAR,
    config = CircularConfig(radius = 10),
    restoreTimeSeconds = 5,
    sequential = true,
    totalSequentialTime = 2.0f,
    randomSize = true  // 각 블럭의 사이즈가 랜덤하게 변경됨
)
```

---

### 7. 동작 원리

1. **블럭 감지**: 지정된 감지 유형에 따라 블럭 위치 리스트 생성
2. **블럭 스캔**: 각 위치에서 위에서 아래로 스캔하여 가장 높은 블럭 찾기
3. **블럭 변환**: 찾은 블럭을 BARRIER로 변경하고 원본 BlockData 저장
4. **BlockDisplay 소환**: 원본 블럭의 BlockDisplay를 소환하고 회전값 적용
5. **랜덤 사이즈 적용** (옵션): `randomSize = true`일 때 각 BlockDisplay의 사이즈를 0.9~1.2 범위로 랜덤 변경
6. **파티클 효과**: 블럭 변환 시 원본 블럭의 파티클 효과 발생
7. **복원 스케줄링**: 지정된 시간 후 랜덤 순서로 블럭 복원
8. **애니메이션 복원**: BlockDisplay의 회전값을 0으로 부드럽게 애니메이션
9. **블럭 복원**: BlockDisplay 제거 후 원본 블럭으로 복원

---

### 8. 주의사항

1. **부서지기 쉬운 블럭 제외**: 풀, 꽃, 문, 작물 등은 자동으로 제외됩니다.
2. **재적용 시 동작**: 이미 지진 효과가 적용된 블럭에 다시 적용하면 복원 시간이 초기화되고 새로운 회전값이 적용됩니다.
3. **청크 로드**: BlockDisplay 소환 전에 청크가 로드되어 있는지 확인합니다.
4. **성능 고려**: 큰 반지름이나 많은 커스텀 위치는 성능에 영향을 줄 수 있습니다.

---

### 9. API 참조

#### DetectionType Enum
- `CIRCULAR` - 원형 감지
- `FORWARD_RECTANGLE` - 전방 사각형 감지
- `CIRCULAR_EDGE` - 원형 가장자리 감지
- `CUSTOM_LOCATIONS` - 커스텀 위치 감지

#### EarthquakeType Enum
- `UNIFORM` - 고르게 회전
- `DISTANCE_BASED` - 거리 기반 회전 (원형 전용)
- `DISTANCE_BASED_CENTER` - 거리 기반 회전 + 중심 향하기 (원형 전용)
- `WIDTH_BASED` - 넓이 기반 회전 (전방 사각형 전용)
- `WIDTH_BASED_CENTER` - 넓이 기반 회전 + 중심 향하기 (전방 사각형 전용)

#### Config 클래스
- `CircularConfig(radius: Int)` - 원형 감지 설정
- `ForwardRectangleConfig(width: Int, length: Int)` - 전방 사각형 감지 설정
- `CircularEdgeConfig(radius: Int, edgeRange: Int)` - 원형 가장자리 감지 설정

#### Earthquake 클래스
- `Earthquake(plugin: Plugin)` - 생성자
- `execute(...)` - 지진 효과 실행

---

## 컷씬 API

컷씬 API는 저장된 컷씬을 재생하고 관리하는 기능을 제공합니다.

### 주요 기능
- **컷씬 재생**: 저장된 컷씬을 플레이어에게 재생
- **컷씬 관리**: 컷씬 생성, 로드, 저장, 삭제
- **컷씬 목록**: 저장된 모든 컷씬 목록 조회

---

### 1. 컷씬 재생

저장된 컷씬을 플레이어에게 재생합니다.

```kotlin
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

val player: Player = // 플레이어
val sceneName: String = "intro_scene" // 저장된 컷씬 이름

// 컷씬 재생
val success = CinematicCoreAPI.Cutscene.play(sceneName, player)

if (success) {
    player.sendMessage("§a컷씬 '$sceneName' 재생 시작")
} else {
    player.sendMessage("§c컷씬 '$sceneName' 재생 실패 (존재하지 않거나 이미 재생 중)")
}
```

**매개변수:**
- `sceneName`: 재생할 컷씬 이름
- `player`: 컷씬을 재생할 플레이어

**반환값:** `Boolean` - 재생 성공 여부

**참고:**
- 컷씬 재생 중에는 플레이어가 스펙터 모드로 전환되어 카메라 엔티티에 빙의됩니다.
- 재생이 완료되거나 중단되면 플레이어는 원래 위치와 게임 모드로 복원됩니다.
- 이미 재생 중인 플레이어는 다른 컷씬을 재생할 수 없습니다.

---

### 2. 컷씬 생성

새로운 컷씬을 생성합니다.

```kotlin
val sceneName: String = "new_scene"
val player: Player = // 플레이어

val success = CinematicCoreAPI.Cutscene.create(sceneName, player)

if (success) {
    player.sendMessage("§a컷씬 '$sceneName' 생성 완료")
} else {
    player.sendMessage("§c컷씬 생성 실패 (이미 존재하거나 권한 없음)")
}
```

**매개변수:**
- `sceneName`: 생성할 컷씬 이름
- `player`: 컷씬을 생성할 플레이어

**반환값:** `Boolean` - 생성 성공 여부

**참고:** 생성된 컷씬은 빈 컷씬이며, 게임 내에서 `/cc scene edit [sceneName]` 명령어로 키프레임을 추가해야 합니다.

---

### 3. 컷씬 로드

저장된 컷씬을 로드합니다.

```kotlin
val sceneName: String = "intro_scene"

val cutscene = CinematicCoreAPI.Cutscene.load(sceneName)

cutscene?.let {
    println("컷씬 이름: ${it.name}")
    println("키프레임 개수: ${it.keyframes.size}")
    
    // 키프레임 정보 확인
    it.keyframes.forEach { keyframe ->
        println("키프레임 #${keyframe.index}: ${keyframe.location.x}, ${keyframe.location.y}, ${keyframe.location.z}")
    }
} ?: println("컷씬을 찾을 수 없습니다")
```

**매개변수:**
- `sceneName`: 로드할 컷씬 이름

**반환값:** `Cutscene?` - 로드된 컷씬, 없으면 null

---

### 4. 컷씬 저장

컷씬을 저장합니다.

```kotlin
val cutscene: Cutscene = // 저장할 컷씬 객체

val success = CinematicCoreAPI.Cutscene.save(cutscene)

if (success) {
    println("컷씬 저장 성공")
} else {
    println("컷씬 저장 실패")
}
```

**매개변수:**
- `cutscene`: 저장할 컷씬 객체

**반환값:** `Boolean` - 저장 성공 여부

**참고:** 일반적으로 게임 내에서 `/cc scene edit` 명령어로 편집한 컷씬은 자동으로 저장됩니다.

---

### 5. 컷씬 삭제

저장된 컷씬을 삭제합니다.

```kotlin
val sceneName: String = "old_scene"

val success = CinematicCoreAPI.Cutscene.delete(sceneName)

if (success) {
    println("컷씬 '$sceneName' 삭제 완료")
} else {
    println("컷씬 삭제 실패 (존재하지 않음)")
}
```

**매개변수:**
- `sceneName`: 삭제할 컷씬 이름

**반환값:** `Boolean` - 삭제 성공 여부

---

### 6. 컷씬 목록 조회

저장된 모든 컷씬 목록을 가져옵니다.

```kotlin
val scenes = CinematicCoreAPI.Cutscene.list()

println("저장된 컷씬: ${scenes.size}개")
scenes.forEach { sceneName ->
    println("- $sceneName")
}

// 플레이어에게 목록 표시
player.sendMessage("§a저장된 컷씬: §f${scenes.joinToString(", ")}")
```

**반환값:** `List<String>` - 컷씬 이름 리스트

---

### 7. 컷씬 재생 강제 종료

재생 중인 컷씬을 강제로 종료합니다.

```kotlin
val player: Player = // 플레이어

// 기본 사용법: 재생 전 위치로 복원 (기본값)
val success = CinematicCoreAPI.Cutscene.stop(player)

if (success) {
    player.sendMessage("§a컷씬 재생이 중지되었습니다.")
} else {
    player.sendMessage("§c재생 중인 컷씬이 없습니다.")
}

// 현재 위치에서 멈추기
CinematicCoreAPI.Cutscene.stop(player, restoreOriginalLocation = false)
```

**매개변수:**
- `player`: 컷씬을 종료할 플레이어
- `restoreOriginalLocation`: 위치 복원 여부 (기본값: `true`)
  - `true`: 재생 전 위치로 복원
  - `false`: 현재 위치(재생 중단 위치)에서 멈춤

**반환값:** `Boolean` - 종료 성공 여부 (재생 중이었으면 true, 아니면 false)

**참고:** 
- `restoreOriginalLocation = true`일 때: 컷씬이 종료되면 플레이어는 재생 전 위치와 게임 모드로 복원됩니다.
- `restoreOriginalLocation = false`일 때: 컷씬이 종료되면 플레이어는 현재 위치(재생 중단 위치)에서 멈추고 게임 모드만 복원됩니다.

---

### 8. 컷씬 재생 상태 확인

플레이어가 현재 컷씬을 재생 중인지 확인합니다.

```kotlin
val player: Player = // 플레이어

if (CinematicCoreAPI.Cutscene.isPlaying(player)) {
    player.sendMessage("§a컷씬 재생 중입니다.")
} else {
    player.sendMessage("§7재생 중인 컷씬이 없습니다.")
}
```

**매개변수:**
- `player`: 확인할 플레이어

**반환값:** `Boolean` - 재생 중이면 true

---

### 9. 사용 예제

#### 예제 1: 컷씬 재생 및 상태 확인

```kotlin
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun playCutsceneToPlayer(player: Player, sceneName: String) {
    // 1. 컷씬 존재 여부 확인
    val cutscene = CinematicCoreAPI.Cutscene.load(sceneName)
    if (cutscene == null) {
        player.sendMessage("§c컷씬 '$sceneName'을 찾을 수 없습니다.")
        return
    }
    
    // 2. 키프레임 개수 확인
    val keyframeCount = cutscene.keyframes.size
    if (keyframeCount < 2) {
        player.sendMessage("§c컷씬에 키프레임이 부족합니다 (최소 2개 필요)")
        return
    }
    
    player.sendMessage("§a컷씬 정보:")
    player.sendMessage("§7- 이름: $sceneName")
    player.sendMessage("§7- 키프레임: $keyframeCount개")
    
    // 3. 컷씬 재생
    val success = CinematicCoreAPI.Cutscene.play(sceneName, player)
    if (success) {
        player.sendMessage("§a컷씬 재생 시작!")
    } else {
        player.sendMessage("§c컷씬 재생 실패 (이미 재생 중일 수 있습니다)")
    }
}
```

#### 예제 2: 모든 컷씬 목록 표시

```kotlin
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun showAllCutscenes(player: Player) {
    val scenes = CinematicCoreAPI.Cutscene.list()
    
    if (scenes.isEmpty()) {
        player.sendMessage("§7저장된 컷씬이 없습니다.")
        return
    }
    
    player.sendMessage("§a=== 저장된 컷씬 목록 ===")
    scenes.forEachIndexed { index, sceneName ->
        val cutscene = CinematicCoreAPI.Cutscene.load(sceneName)
        val keyframeCount = cutscene?.keyframes?.size ?: 0
        player.sendMessage("§7${index + 1}. §f$sceneName §7($keyframeCount개 키프레임)")
    }
}
```

#### 예제 3: 컷씬 정보 상세 조회

```kotlin
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun showCutsceneDetails(sceneName: String) {
    val cutscene = CinematicCoreAPI.Cutscene.load(sceneName) ?: run {
        println("컷씬을 찾을 수 없습니다: $sceneName")
        return
    }
    
    println("=== 컷씬 정보 ===")
    println("이름: ${cutscene.name}")
    println("키프레임 개수: ${cutscene.keyframes.size}")
    println()
    
    cutscene.keyframes.forEach { keyframe ->
        println("키프레임 #${keyframe.index}:")
        println("  위치: ${keyframe.location.x}, ${keyframe.location.y}, ${keyframe.location.z}")
        println("  월드: ${keyframe.location.world?.name}")
        println("  Pitch: ${keyframe.pitch}°")
        println("  Yaw: ${keyframe.yaw}°")
        println("  보간 방식: ${keyframe.interpolationType}")
        keyframe.durationSeconds?.let {
            println("  구간 시간: ${it}초")
        } ?: println("  구간 시간: 자동 (거리 기반)")
        println()
    }
}
```

---

### 8. 주의사항

1. **컷씬 파일 위치**: 컷씬은 `plugins/CinematicCore/scene/[씬이름].json` 경로에 저장됩니다.

2. **재생 중 중복 방지**: 한 플레이어가 이미 컷씬을 재생 중이면 다른 컷씬을 재생할 수 없습니다.

3. **키프레임 최소 개수**: 컷씬을 재생하려면 최소 2개 이상의 키프레임이 필요합니다.

4. **빙의 해제 방지**: 컷씬 재생 중에는 플레이어가 빙의를 해제할 수 없습니다. (`PlayerStopSpectatingEntityEvent`가 취소됩니다)

5. **게임 모드 변경**: 컷씬 재생 중 플레이어는 자동으로 스펙터 모드로 전환되며, 재생 완료 후 원래 게임 모드로 복원됩니다.

---

### 9. API 참조

#### Cutscene API
- `play(sceneName, player)` - 컷씬 재생
- `create(sceneName, player)` - 컷씬 생성
- `load(sceneName)` - 컷씬 로드
- `save(cutscene)` - 컷씬 저장
- `delete(sceneName)` - 컷씬 삭제
- `list()` - 컷씬 목록 조회
- `stop(player, restoreOriginalLocation?)` - 컷씬 재생 강제 종료
- `isPlaying(player)` - 컷씬 재생 상태 확인

#### Cutscene 데이터 구조
- `Cutscene`: 컷씬 객체
  - `name: String` - 컷씬 이름
  - `keyframes: List<Keyframe>` - 키프레임 리스트

- `Keyframe`: 키프레임 객체
  - `index: Int` - 키프레임 인덱스
  - `location: Location` - 위치
  - `pitch: Float` - 수직 각도
  - `yaw: Float` - 수평 각도
  - `interpolationType: InterpolationType` - 보간 방식 (LINEAR, BEZIER)
  - `durationSeconds: Double?` - 구간 시간 (null이면 자동 계산)

---

## 전체 예제

### 예제 1: 모델 적용 및 애니메이션 재생

```kotlin
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun applyModelAndPlayAnimation(player: Player, location: Location) {
    // 1. 엔티티 생성 (예: Silverfish)
    val entity = location.world.spawn(location, org.bukkit.entity.Silverfish::class.java)
    entity.setAI(false)
    
    // 2. 모델 적용
    val success = CinematicCoreAPI.Model.applyModel(
        entity = entity,
        modelName = "player_battle",
        location = location
    )
    
    if (!success) {
        player.sendMessage("모델 적용 실패")
        return
    }
    
    player.sendMessage("모델 적용 성공!")
    
    // 3. 모델 정보 확인
    val model = CinematicCoreAPI.Model.getModel(entity)
    model?.let {
        player.sendMessage("모델 이름: ${it.modelName}")
        player.sendMessage("애니메이션 개수: ${it.animations.size}")
    }
    
    // 4. 애니메이션 재생
    val animationSuccess = CinematicCoreAPI.Animation.playAnimationByName(
        entity = entity,
        animationName = "idle",
        speed = 1.0f
    )
    
    if (animationSuccess) {
        player.sendMessage("애니메이션 재생 시작")
    }
}
```

### 예제 2: Bone 위치 추적 및 엔티티 탑승

```kotlin
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.api.CinematicCoreAPI
import org.example.hoon.cinematicCore.CinematicCore

fun trackBoneAndMount(entity: Entity, player: Player) {
    // 1. Bone 위치 확인
    val headLocation = CinematicCoreAPI.Bone.getBoneLocation(entity, "head")
    headLocation?.let {
        player.sendMessage("Head bone 위치: ${it.x}, ${it.y}, ${it.z}")
    }
    
    // 2. Bone 계층 구조 확인
    val hierarchy = CinematicCoreAPI.Bone.getBoneHierarchy(entity, "head")
    hierarchy?.let {
        player.sendMessage("Head bone의 부모: ${it.parent?.name ?: "없음"}")
        player.sendMessage("Head bone의 자식: ${it.children.joinToString { c -> c.name }}")
    }
    
    // 3. 플레이어를 head bone에 탑승 (내릴 수 없게 설정)
    val mountSuccess = CinematicCoreAPI.Bone.mountEntityOnBone(
        entity = entity,
        boneName = "head",
        passenger = player,
        canDismount = false  // 내릴 수 없음
    )
    
    if (mountSuccess) {
        player.sendMessage("Head bone에 탑승했습니다 (내릴 수 없음)")
        
        // 4. 탑승 여부 및 내림 가능 여부 확인
        val isMounted = CinematicCoreAPI.Bone.isEntityMountedOnBone(entity, player)
        val canDismount = CinematicCoreAPI.Bone.canPassengerDismount(entity, player)
        player.sendMessage("탑승 여부: $isMounted")
        player.sendMessage("내릴 수 있는지: ${canDismount ?: "알 수 없음"}")
        
        // 5. Bone 위치를 주기적으로 추적
        object : BukkitRunnable() {
            override fun run() {
                if (!entity.isValid || !player.isValid) {
                    cancel()
                    return
                }
                
                val location = CinematicCoreAPI.Bone.getBoneLocation(entity, "head")
                location?.let {
                    // Bone 위치 정보를 플레이어에게 전송
                    // player.sendActionBar("Head 위치: ${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()}")
                }
            }
        }.runTaskTimer(CinematicCore.instance, 0L, 20L) // 1초마다
        
        // 6. 10초 후 강제로 내리기 예제
        Bukkit.getScheduler().runTaskLater(CinematicCore.instance, Runnable {
            val dismountSuccess = CinematicCoreAPI.Bone.dismountEntityFromBone(entity, player)
            if (dismountSuccess) {
                player.sendMessage("강제로 내렸습니다")
            }
        }, 200L) // 10초 후
    }
}
```

### 예제 3: 애니메이션 시퀀스 제어

```kotlin
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.api.CinematicCoreAPI
import org.example.hoon.cinematicCore.CinematicCore

fun playAnimationSequence(entity: Entity) {
    val model = CinematicCoreAPI.Model.getModel(entity) ?: return
    
    // 1. 사용 가능한 애니메이션 확인
    val animations = model.animations
    println("사용 가능한 애니메이션: ${animations.joinToString { it.name }}")
    
    // 2. 애니메이션 시퀀스 재생
    var currentIndex = 0
    
    fun playNextAnimation() {
        if (currentIndex >= animations.size) {
            println("모든 애니메이션 재생 완료")
            return
        }
        
        val animation = animations[currentIndex]
        println("애니메이션 재생: ${animation.name}")
        
        // 애니메이션 재생 (중단 불가능)
        CinematicCoreAPI.Animation.playAnimation(
            entity = entity,
            animation = animation,
            speed = 1.0f,
            interruptible = false
        )
        
        // 애니메이션 길이만큼 대기 후 다음 애니메이션 재생
        object : BukkitRunnable() {
            override fun run() {
                // 애니메이션이 끝났는지 확인
                if (!CinematicCoreAPI.Animation.isAnimationPlaying(entity)) {
                    currentIndex++
                    playNextAnimation()
                } else {
                    // 아직 재생 중이면 다시 확인
                    runTaskLater(CinematicCore.instance, 20L) // 1초 후 다시 확인
                }
            }
        }.runTaskLater(CinematicCore.instance, (animation.length * 20).toLong())
    }
    
    // 첫 번째 애니메이션 재생 시작
    playNextAnimation()
}
```

### 예제 4: Bone 교체 및 모델 정보 확인

```kotlin
import org.bukkit.entity.Entity
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun replaceBoneAndCheckModel(entity: Entity) {
    // 1. 원본 모델 확인
    val originalModel = CinematicCoreAPI.Model.getModel(entity)
    originalModel?.let {
        println("원본 모델: ${it.modelName}")
    }
    
    // 2. Head bone의 원본 모델 확인
    val headBoneModel = CinematicCoreAPI.Model.getBoneModel(entity, "head")
    headBoneModel?.let {
        println("Head bone 모델: ${it.modelName}")
    }
    
    // 3. Head bone을 다른 모델의 bone으로 교체
    val replaceSuccess = CinematicCoreAPI.Bone.replaceBone(
        entity = entity,
        targetBoneName = "head",
        replacementModelName = "player_battle",
        replacementBoneName = "head"
    )
    
    if (replaceSuccess) {
        println("Head bone 교체 성공")
        
        // 4. 교체 후 Head bone의 모델 확인
        val newHeadBoneModel = CinematicCoreAPI.Model.getBoneModel(entity, "head")
        newHeadBoneModel?.let {
            println("교체 후 Head bone 모델: ${it.modelName}")
        }
    }
    
    // 5. Base Entity 확인
    val baseEntity = CinematicCoreAPI.Model.getBoneBaseEntity(entity, "head")
    baseEntity?.let {
        println("Base Entity: ${it.type}")
    }
}
```

### 예제 5: 특정 모델이 적용된 모든 엔티티 관리

```kotlin
import org.bukkit.World
import org.bukkit.entity.Entity
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun manageEntitiesWithModel(modelName: String, world: World?) {
    // 1. 특정 모델이 적용된 모든 엔티티 찾기
    val entities = CinematicCoreAPI.Model.getEntitiesWithModel(modelName, world)
    println("${modelName} 모델이 적용된 엔티티: ${entities.size}개")
    
    // 2. 각 엔티티에 대해 작업 수행
    entities.forEach { entity ->
        println("- ${entity.type} at ${entity.location}")
        
        // 3. 애니메이션 상태 확인
        val isPlaying = CinematicCoreAPI.Animation.isAnimationPlaying(entity)
        println("  애니메이션 재생 중: $isPlaying")
        
        // 4. 현재 애니메이션 확인
        val currentAnimation = CinematicCoreAPI.Animation.getCurrentAnimation(entity)
        currentAnimation?.let {
            println("  현재 애니메이션: ${it.name}")
        }
        
        // 5. 데미지 색상 변경 기능 확인
        val damageColorEnabled = CinematicCoreAPI.Model.isDamageColorChangeEnabled(entity)
        damageColorEnabled?.let {
            println("  데미지 색상 변경: ${if (it) "활성화" else "비활성화"}")
        }
        
        // 6. 특정 bone 위치 확인
        val headLocation = CinematicCoreAPI.Bone.getBoneLocation(entity, "head")
        headLocation?.let {
            println("  Head bone 위치: ${it.x}, ${it.y}, ${it.z}")
        }
    }
    
    // 7. 모든 엔티티에 동일한 애니메이션 재생
    entities.forEach { entity ->
        CinematicCoreAPI.Animation.playAnimationByName(
            entity = entity,
            animationName = "idle",
            speed = 1.0f
        )
    }
}
```

### EventKeyframe 이벤트 사용 가이드

Blockbench 애니메이션의 `effects` 섹션(채널 `timeline`, type `effect`)에 `script` 값을 가진 키프레임을 추가하면 CinematicCore가 이를 `EventKeyframe`으로 파싱합니다.

```jsonc
"effects": {
    "type": "effect",
    "keyframes": [
        {
            "channel": "timeline",
            "time": 1.0,
            "data_points": [
                { "script": "phase1" }
            ]
        }
    ]
}
```

해당 애니메이션이 재생될 때, 키프레임 시점에 `script` 이름으로 이벤트가 발송됩니다. 서버 코드에서는 `AnimationEventAPI`를 사용해 엔티티별 리스너를 등록하면 됩니다.

```kotlin
import org.bukkit.entity.LivingEntity
import org.example.hoon.cinematicCore.api.animation.AnimationEventAPI
import org.example.hoon.cinematicCore.util.animation.event.AnimationEventContext

fun registerBossEvents(boss: LivingEntity) {
    val receiver = AnimationEventAPI.register(boss)

    receiver.on("phase1") { ctx: AnimationEventContext ->
        boss.customName = "§cPhase 1"
        boss.world.strikeLightningEffect(boss.location)
        ctx.animation.name // 현재 애니메이션 이름
        ctx.model.modelName // 적용된 모델 정보
    }

    receiver.on("phase2") {
        boss.customName = "§4Enraged"
    }
}
```

- `AnimationEventContext`에는 baseEntity, 모델, 애니메이션, 발생한 `EventKeyframe`(uuid/time/script) 정보가 담겨 있습니다.
- 동일 엔티티에서 애니메이션이 루프되면 같은 `script`도 루프마다 다시 발송됩니다.
- 엔티티 제거 시 `DisplaySession`이 자동으로 리스너를 정리하지만, 수동으로 해제해야 할 경우 `AnimationEventAPI.unregister(entity)`를 호출하세요.

### 예제 6: 데미지 색상 변경 기능 제어

```kotlin
import org.bukkit.entity.Entity
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun controlDamageColorChange(entity: Entity) {
    // 1. 현재 설정 확인
    val currentSetting = CinematicCoreAPI.Model.isDamageColorChangeEnabled(entity)
    currentSetting?.let {
        println("현재 데미지 색상 변경: ${if (it) "활성화" else "비활성화"}")
    }
    
    // 2. 데미지 색상 변경 비활성화 (데미지를 받아도 빨간색으로 변하지 않음)
    val success1 = CinematicCoreAPI.Model.setDamageColorChangeEnabled(entity, false)
    if (success1) {
        println("데미지 색상 변경 비활성화됨")
    }
    
    // 3. 데미지 색상 변경 활성화 (데미지를 받으면 빨간색으로 변함)
    val success2 = CinematicCoreAPI.Model.setDamageColorChangeEnabled(entity, true)
    if (success2) {
        println("데미지 색상 변경 활성화됨")
    }
    
    // 4. 모델 적용 시 데미지 색상 변경 비활성화
    // (이미 적용된 모델이 있다면 위의 setDamageColorChangeEnabled를 사용)
    // 새로 모델을 적용할 때:
    // CinematicCoreAPI.Model.applyModel(
    //     entity = entity,
    //     modelName = "player_battle",
    //     location = location,
    //     enableDamageColorChange = false
    // )
}
```

### 예제 7: Bone 탑승 제어 (내릴 수 없게 설정)

```kotlin
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

fun controlBoneMounting(entity: Entity, player: Player) {
    // 1. 플레이어를 head bone에 탑승 (내릴 수 없게 설정)
    val mountSuccess = CinematicCoreAPI.Bone.mountEntityOnBone(
        entity = entity,
        boneName = "head",
        passenger = player,
        canDismount = false  // 내릴 수 없음
    )
    
    if (mountSuccess) {
        println("플레이어가 head bone에 탑승했습니다 (내릴 수 없음)")
        
        // 2. 탑승 여부 확인
        val isMounted = CinematicCoreAPI.Bone.isEntityMountedOnBone(entity, player)
        println("탑승 여부: $isMounted")
        
        // 3. 내림 가능 여부 확인
        val canDismount = CinematicCoreAPI.Bone.canPassengerDismount(entity, player)
        canDismount?.let {
            println("내릴 수 있는지: $it")
        }
        
        // 4. 플레이어가 Shift 키를 눌러도 내려가지 않음
        // 5. 강제로 내리기 (필요한 경우)
        // val dismountSuccess = CinematicCoreAPI.Bone.dismountEntityFromBone(entity, player)
        // if (dismountSuccess) {
        //     println("강제로 내렸습니다")
        // }
    }
    
    // 6. 내릴 수 있게 설정하여 탑승
    val mountSuccess2 = CinematicCoreAPI.Bone.mountEntityOnBone(
        entity = entity,
        boneName = "body",
        passenger = player,
        canDismount = true  // 내릴 수 있음 (기본값)
    )
    
    if (mountSuccess2) {
        println("플레이어가 body bone에 탑승했습니다 (내릴 수 있음)")
        // 이제 플레이어는 Shift 키를 눌러 내릴 수 있습니다
    }
}
```

### 예제 8: 컷씬 재생 및 관리

```kotlin
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.example.hoon.cinematicCore.api.CinematicCoreAPI
import org.example.hoon.cinematicCore.CinematicCore

fun playCutsceneSequence(player: Player) {
    // 1. 저장된 컷씬 목록 확인
    val scenes = CinematicCoreAPI.Cutscene.list()
    if (scenes.isEmpty()) {
        player.sendMessage("§c저장된 컷씬이 없습니다.")
        return
    }
    
    player.sendMessage("§a저장된 컷씬: §f${scenes.joinToString(", ")}")
    
    // 2. 첫 번째 컷씬 재생
    val firstScene = scenes.first()
    val success = CinematicCoreAPI.Cutscene.play(firstScene, player)
    
    if (success) {
        player.sendMessage("§a컷씬 '$firstScene' 재생 시작")
        
        // 3. 컷씬 정보 확인
        val cutscene = CinematicCoreAPI.Cutscene.load(firstScene)
        cutscene?.let {
            player.sendMessage("§7키프레임 개수: ${it.keyframes.size}개")
            
            // 4. 컷씬 재생 완료 대기 (예제)
            // 실제로는 컷씬이 자동으로 완료되면 플레이어가 원래 위치로 복원됩니다
            object : BukkitRunnable() {
                override fun run() {
                    // 컷씬 재생이 완료되었는지 확인하는 로직
                    // (실제 구현에서는 이벤트나 콜백을 사용하는 것이 좋습니다)
                }
            }.runTaskLater(CinematicCore.instance, 100L) // 5초 후
        }
    } else {
        player.sendMessage("§c컷씬 재생 실패")
    }
}

fun showCutsceneInfo(player: Player, sceneName: String) {
    val cutscene = CinematicCoreAPI.Cutscene.load(sceneName)
    
    if (cutscene == null) {
        player.sendMessage("§c컷씬 '$sceneName'을 찾을 수 없습니다.")
        return
    }
    
    player.sendMessage("§a=== 컷씬 정보 ===")
    player.sendMessage("§7이름: §f${cutscene.name}")
    player.sendMessage("§7키프레임 개수: §f${cutscene.keyframes.size}개")
    
    cutscene.keyframes.forEach { keyframe ->
        val interpolationType = when (keyframe.interpolationType) {
            org.example.hoon.cinematicCore.cutscene.InterpolationType.LINEAR -> "선형"
            org.example.hoon.cinematicCore.cutscene.InterpolationType.BEZIER -> "베지어"
        }
        val duration = keyframe.durationSeconds?.let { "${it}초" } ?: "자동"
        
        player.sendMessage("§7키프레임 #${keyframe.index}:")
        player.sendMessage("§7  위치: §f${keyframe.location.blockX}, ${keyframe.location.blockY}, ${keyframe.location.blockZ}")
        player.sendMessage("§7  보간: §f$interpolationType")
        player.sendMessage("§7  시간: §f$duration")
    }
}

fun stopCutsceneExample(player: Player) {
    // 1. 재생 중인지 확인
    if (!CinematicCoreAPI.Cutscene.isPlaying(player)) {
        player.sendMessage("§c재생 중인 컷씬이 없습니다.")
        return
    }
    
    // 2. 컷씬 강제 종료 (재생 전 위치로 복원)
    val success = CinematicCoreAPI.Cutscene.stop(player, restoreOriginalLocation = true)
    
    if (success) {
        player.sendMessage("§a컷씬 재생이 중지되었습니다. (원래 위치로 복원)")
    } else {
        player.sendMessage("§c컷씬 종료 실패")
    }
}

fun stopCutsceneAtCurrentLocation(player: Player) {
    // 현재 위치에서 멈추기
    val success = CinematicCoreAPI.Cutscene.stop(player, restoreOriginalLocation = false)
    
    if (success) {
        player.sendMessage("§a컷씬 재생이 중지되었습니다. (현재 위치에서 멈춤)")
    }
}

fun conditionalStopCutscene(player: Player, condition: () -> Boolean) {
    // 조건에 따라 컷씬을 중지하는 예제
    if (CinematicCoreAPI.Cutscene.isPlaying(player) && condition()) {
        CinematicCoreAPI.Cutscene.stop(player)
        player.sendMessage("§a조건에 따라 컷씬이 중지되었습니다.")
    }
}
```

---

## 주의사항

1. **엔티티 유효성 확인**: 모든 API 메서드를 사용하기 전에 엔티티가 유효한지 확인하세요.
   ```kotlin
   if (!entity.isValid) return
   ```

2. **모델 적용 확인**: Bone이나 애니메이션 관련 API를 사용하기 전에 모델이 적용되어 있는지 확인하세요.
   ```kotlin
   if (!CinematicCoreAPI.Model.hasModel(entity)) {
       println("모델이 적용되어 있지 않습니다")
       return
   }
   ```

3. **Null 안전성**: 대부분의 API 메서드는 null을 반환할 수 있으므로 null-safe 연산자를 사용하세요.
   ```kotlin
   val model = CinematicCoreAPI.Model.getModel(entity) ?: return
   ```

4. **성능 고려**: `getEntitiesWithModel()`은 모든 월드의 엔티티를 검색하므로, 가능하면 특정 월드를 지정하세요.

5. **애니메이션 중단**: `interruptible = false`로 설정된 애니메이션은 다른 애니메이션으로 교체할 수 없습니다.

---

## API 참조

### Model API
- `applyModel(entity, modelName, location?, enableDamageColorChange?)` - 모델 적용
- `getModel(entity)` - 모델 가져오기
- `getBoneModel(entity, boneName)` - Bone 모델 가져오기
- `getBoneBaseEntity(entity, boneName)` - Bone baseEntity 가져오기
- `getEntitiesWithModel(modelName, world?)` - 모델이 적용된 엔티티 찾기
- `hasModel(entity)` - 모델 적용 여부 확인
- `getModelInfo(entity)` - 모델 정보 가져오기
- `setDamageColorChangeEnabled(entity, enable)` - 데미지 색상 변경 기능 설정
- `isDamageColorChangeEnabled(entity)` - 데미지 색상 변경 기능 확인

### Animation API
- `playAnimation(entity, animation, speed, interruptible)` - 애니메이션 재생 (객체)
- `playAnimationByName(entity, animationName, speed, interruptible)` - 애니메이션 재생 (이름)
- `AnimationEventAPI.register(entity)` / `on(entity, script, handler)` - EventKeyframe 신호 수신
- `stopAnimation(entity, resetPose)` - 애니메이션 정지
- `pauseAnimation(entity)` - 애니메이션 일시정지
- `resumeAnimation(entity)` - 애니메이션 재개
- `findAnimation(model, animationName)` - 애니메이션 찾기
- `getCurrentAnimation(entity)` - 현재 애니메이션 가져오기
- `isAnimationPlaying(entity)` - 재생 여부 확인

### Bone API
- `mountEntityOnBone(entity, boneName, passenger, offset?)` - 엔티티 탑승
- `replaceBone(entity, targetBoneName, replacementModelName, replacementBoneName?)` - Bone 교체
- `getBoneLocation(entity, boneName)` - Bone 위치 가져오기
- `getBoneTags(entity, boneName)` - Bone 태그 가져오기
- `getBoneHierarchy(entity, boneName)` - Bone 계층 구조 가져오기
- `getBoneDisplay(entity, boneName)` - Bone Display 엔티티 가져오기

### Cutscene API
- `play(sceneName, player)` - 컷씬 재생
- `create(sceneName, player)` - 컷씬 생성
- `load(sceneName)` - 컷씬 로드
- `save(cutscene)` - 컷씬 저장
- `delete(sceneName)` - 컷씬 삭제
- `list()` - 컷씬 목록 조회

---

## 추가 리소스

- **프로젝트 저장소**: [GitHub 링크]
- **이슈 리포트**: [이슈 트래커 링크]
- **문서**: [문서 링크]

---

**문서 버전**: 1.0  
**최종 업데이트**: 2024

