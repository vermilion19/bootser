#!/bin/bash

# ------------------------------------------------------------------
# 사용법: ./init-service-module.sh [부모디렉토리] [모듈명]
# 예시: ./init-service-module.sh apps new-service
# ------------------------------------------------------------------

PARENT_DIR=$1
MODULE_NAME=$2
BASE_PACKAGE="com.booster"

# 1. 입력값 검증
if [ -z "$PARENT_DIR" ] || [ -z "$MODULE_NAME" ]; then
  echo "❌ 사용법 오류: 부모 디렉토리와 모듈명을 모두 입력해주세요."
  echo "👉 예시: ./init-module.sh apps order-service"
  exit 1
fi

TARGET_DIR="$PARENT_DIR/$MODULE_NAME"

# 2. 이미 존재하는지 확인
if [ -d "$TARGET_DIR" ]; then
  echo "❌ 이미 존재하는 디렉토리입니다: $TARGET_DIR"
  exit 1
fi

# 3. 패키지명 및 클래스명 계산
# 패키지명: 하이픈 제거 (예: new-service -> newservice)
PACKAGE_SUFFIX=$(echo "$MODULE_NAME" | tr -d '-')
FULL_PACKAGE="${BASE_PACKAGE}.${PACKAGE_SUFFIX}"

# 패키지 경로 (예: com/booster/newservice)
RELATIVE_PKG_PATH="${BASE_PACKAGE//.//}/$PACKAGE_SUFFIX"

# 클래스명: 케밥케이스 -> 파스칼케이스 변환 (예: new-service -> NewService)
CLASS_PREFIX=$(echo "$MODULE_NAME" | awk -F- '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2)} 1' OFS='')
CLASS_NAME="${CLASS_PREFIX}Application"
TEST_CLASS_NAME="${CLASS_PREFIX}ApplicationTests"

echo "🚀 모듈 생성 시작..."
echo "📂 위치: $TARGET_DIR"
echo "📦 패키지: $FULL_PACKAGE"
echo "☕️ 클래스: $CLASS_NAME / $TEST_CLASS_NAME"

# 4. 디렉토리 구조 생성
MAIN_DIR="$TARGET_DIR/src/main/java/$RELATIVE_PKG_PATH"
TEST_DIR="$TARGET_DIR/src/test/java/$RELATIVE_PKG_PATH"
RES_DIR="$TARGET_DIR/src/main/resources"

mkdir -p "$MAIN_DIR"
mkdir -p "$TEST_DIR"
mkdir -p "$RES_DIR"

# 5. build.gradle 생성 (공통 모듈 의존성 제거됨)
cat <<EOF > "$TARGET_DIR/build.gradle"
plugins {
    id 'java'
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    // 테스트 필수 의존성
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
EOF

# 6. 메인 Application.java 생성
cat <<EOF > "$MAIN_DIR/${CLASS_NAME}.java"
package ${FULL_PACKAGE};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ${CLASS_NAME} {

    public static void main(String[] args) {
        SpringApplication.run(${CLASS_NAME}.class, args);
    }

}
EOF

# 7. 테스트 ApplicationTests.java 생성
cat <<EOF > "$TEST_DIR/${TEST_CLASS_NAME}.java"
package ${FULL_PACKAGE};

import org.junit.jupiter.api.Test;

class ${TEST_CLASS_NAME} {

    @Test
    void contextLoads() {
    }

}
EOF

# 8. application.yml 생성
cat <<EOF > "$RES_DIR/application.yml"
spring:
  application:
    name: $MODULE_NAME
EOF

# 9. settings.gradle에 include 추가
SETTINGS_FILE="settings.gradle"
INCLUDE_LINE="include '$PARENT_DIR:$MODULE_NAME'"

if grep -Fxq "$INCLUDE_LINE" "$SETTINGS_FILE"; then
    echo "ℹ️ settings.gradle에 이미 등록되어 있습니다."
else
    echo "" >> "$SETTINGS_FILE"
    echo "$INCLUDE_LINE" >> "$SETTINGS_FILE"
    echo "✅ settings.gradle 등록 완료: $INCLUDE_LINE"
fi

echo "🎉 [${MODULE_NAME}] 모듈 생성 완료! Gradle Refresh를 실행해주세요."