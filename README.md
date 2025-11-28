# CinematicCore

## JitPack을 통한 Gradle 의존성 추가

이 프로젝트는 JitPack을 통해 Gradle 의존성으로 사용할 수 있습니다.

### 설정 방법

**Step 1:** 프로젝트의 `settings.gradle.kts` (또는 `settings.gradle`)에 JitPack 저장소 추가:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io") // JitPack 저장소 추가
    }
}
```

또는 `build.gradle.kts`에 직접 추가:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // JitPack 저장소 추가
}
```

**Step 2:** `build.gradle.kts`에 의존성 추가:

```kotlin
dependencies {
    implementation("com.github.Hoone02:CinematicCore:VERSION")
    // 또는 특정 커밋 해시 사용
    // implementation("com.github.Hoone02:CinematicCore:COMMIT_HASH")
}
```

**사용 가능한 버전:**
- 릴리즈 태그: `1.0.0`, `1.1.0` 등
- 커밋 해시: `abc1234` (짧은 해시)
- 브랜치: `main`, `master` 등

### API 사용 예시

```kotlin
import org.example.hoon.cinematicCore.api.CinematicCoreAPI

// 모델 적용
CinematicCoreAPI.Model.applyModel(entity, "player_battle", location)

// 애니메이션 재생
CinematicCoreAPI.Animation.playAnimationByName(entity, "walk")

// Bone 위치 가져오기
val location = CinematicCoreAPI.Bone.getBoneLocation(entity, "head")
```

자세한 API 사용법은 [API_USAGE.md](API_USAGE.md)를 참고하세요.

---

## 모델 처리 파이프라인
- `ModelService` → `ModelFileLoader`로 `.bbmodel` 로드
- `ModelProcessor`가 `TextureMapper`·`ScaleCalculator`·`ElementJsonBuilder`를 조합해 JSON/텍스처 생성
- `ModelFilesystem`이 디렉터리 보장 및 정리 책임 담당

## 런타임 소환 흐름
- `ModelDisplaySpawner.spawnByName` → `DisplayEntityFactory`가 Bone별 `ItemDisplay` 구성
- `DisplaySession`이 base 엔티티, 애니메이션, 회전 동기화를 묶어 수명 주기 관리
- `ScaledBoneCache`로 축소 본 정보를 공유해 스케일을 역산 적용

## 빌드 메모
- `gradlew test` 실행 시 `net.kyori:adventure-text-serializer-ansi` 의존성 누락으로 실패함 (Paper dev-bundle 문제).  
  필요 시 Paper 저장소 동기화 또는 버전 고정이 필요합니다.

