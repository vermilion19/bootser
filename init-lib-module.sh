#!/bin/bash

# ------------------------------------------------------------------
# 설명: 라이브러리(공통) 모듈 생성 스크립트
# 사용법: ./init-lib-module.sh [모듈명]
# 예시: ./init-lib-module.sh common-util
# ------------------------------------------------------------------

MODULE_NAME=$1
PARENT_DIR="libs" # 요구사항: libs 디렉토리 고정
BASE_PACKAGE="com.booster"

# 1. 입력값 검증 (모듈명만 입력받음)
if [ -z "$MODULE_NAME" ]; then
  echo "❌ 사용법 오류: 모듈명을 입력해주세요."
  echo "👉 예시: ./init-lib-module.sh common-util"
  exit 1
fi

TARGET_DIR="$PARENT_DIR/$MODULE_NAME"

# 2. 이미 존재하는지 확인
if [ -d "$TARGET_DIR" ]; then
  echo "❌ 이미 존재하는 디렉토리입니다: $TARGET_DIR"
  exit 1
fi

# 3. 패키지명 계산
# 패키지명: 하이픈 제거 (예: common-util -> commonutil)
PACKAGE_SUFFIX=$(echo "$MODULE_NAME" | tr -d '-')
FULL_PACKAGE="${BASE_PACKAGE}.${PACKAGE_SUFFIX}"
RELATIVE_PKG_PATH="${BASE_PACKAGE//.//}/$PACKAGE_SUFFIX"

echo "🚀 라이브러리 모듈 생성 시작..."
echo "📂 위치: $TARGET_DIR"
echo "📦 패키지: $FULL_PACKAGE"

# 4. 디렉토리 구조 생성
MAIN_DIR="$TARGET_DIR/src/main/java/$RELATIVE_PKG_PATH"
TEST_DIR="$TARGET_DIR/src/test/java/$RELATIVE_PKG_PATH"
RES_DIR="$TARGET_DIR/src/main/resources"
META_INF_DIR="$RES_DIR/META-INF"

mkdir -p "$MAIN_DIR"
mkdir -p "$TEST_DIR"
mkdir -p "$META_INF_DIR"

# 5. 빈 build.gradle 생성 (요구사항: 내용 없음)
touch "$TARGET_DIR/build.gradle"

# 6. application.yml 생성
touch "$RES_DIR/application.yml"

# 7. AutoConfiguration.imports 생성
touch "$META_INF_DIR/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"

# 8. settings.gradle에 include 추가
SETTINGS_FILE="settings.gradle"
INCLUDE_LINE="include '$PARENT_DIR:$MODULE_NAME'"

if grep -Fxq "$INCLUDE_LINE" "$SETTINGS_FILE"; then
    echo "ℹ️ settings.gradle에 이미 등록되어 있습니다."
else
    echo "" >> "$SETTINGS_FILE"
    echo "$INCLUDE_LINE" >> "$SETTINGS_FILE"
    echo "✅ settings.gradle 등록 완료: $INCLUDE_LINE"
fi

echo "🎉 [libs/$MODULE_NAME] 라이브러리 모듈 생성 완료!"
echo "👉 build.gradle이 비어있으니 필요한 의존성을 추가하세요."
echo "👉 Gradle Refresh를 실행해주세요."