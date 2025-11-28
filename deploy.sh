#!/bin/bash

# 버전 입력받기
read -p "1.0.0: " VERSION

# JAR 빌드
echo "빌드 중..."
./gradlew clean jar

# GitHub Release 생성 및 JAR 업로드
echo "배포 중..."
gh release create "v$VERSION" \
    build/libs/*.jar \
    --title "Release v$VERSION" \
    --notes "Release version $VERSION"

echo "✅ 배포 완료!"
echo "https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/v$VERSION"