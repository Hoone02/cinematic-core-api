# CinematicCore

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

