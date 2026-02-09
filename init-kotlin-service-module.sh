#!/bin/bash

# ------------------------------------------------------------------
# 사용법: ./init-kotlin-service-module.sh [모듈명]
# 예시: ./init-kotlin-service-module.sh order-service
# 결과: apps/kotlin/order-service 생성
# ------------------------------------------------------------------

MODULE_NAME=$1
PARENT_DIR="apps/kotlin"
BASE_PACKAGE="com.booster.kotlin"

# 1. 입력값 검증
if [ -z "$MODULE_NAME" ]; then
  echo "❌ 사용법 오류: 모듈명을 입력해주세요."
  echo "👉 예시: ./init-kotlin-service-module.sh order-service"
  exit 1
fi

TARGET_DIR="$PARENT_DIR/$MODULE_NAME"

# 2. 이미 존재하는지 확인
if [ -d "$TARGET_DIR" ]; then
  echo "❌ 이미 존재하는 디렉토리입니다: $TARGET_DIR"
  exit 1
fi

# 3. 패키지명 및 클래스명 계산
# 패키지명: 하이픈을 제거 (예: order-service -> orderservice)
PACKAGE_SUFFIX=$(echo "$MODULE_NAME" | tr -d '-')
FULL_PACKAGE="${BASE_PACKAGE}.${PACKAGE_SUFFIX}"

# 패키지 경로 (예: com/booster/kotlin/orderservice)
RELATIVE_PKG_PATH="${BASE_PACKAGE//.//}/$PACKAGE_SUFFIX"

# 클래스명: 케밥케이스 -> 파스칼케이스 변환 (예: order-service -> OrderService)
CLASS_PREFIX=$(echo "$MODULE_NAME" | awk -F- '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2)} 1' OFS='')
CLASS_NAME="${CLASS_PREFIX}Application"
TEST_CLASS_NAME="${CLASS_PREFIX}ApplicationTests"

echo "🚀 Kotlin 모듈 생성 시작..."
echo "📂 위치: $TARGET_DIR"
echo "📦 패키지: $FULL_PACKAGE"
echo "🟣 클래스: $CLASS_NAME / $TEST_CLASS_NAME"

# 4. 디렉토리 구조 생성
MAIN_DIR="$TARGET_DIR/src/main/kotlin/$RELATIVE_PKG_PATH"
TEST_DIR="$TARGET_DIR/src/test/kotlin/$RELATIVE_PKG_PATH"
RES_DIR="$TARGET_DIR/src/main/resources"
TEST_RES_DIR="$TARGET_DIR/src/test/resources"

mkdir -p "$MAIN_DIR/domain"
mkdir -p "$MAIN_DIR/application"
mkdir -p "$MAIN_DIR/web"
mkdir -p "$TEST_DIR"
mkdir -p "$RES_DIR"
mkdir -p "$TEST_RES_DIR"

# 5. build.gradle.kts 생성 (sample-service 의존성 기반)
cat <<EOF > "$TARGET_DIR/build.gradle.kts"
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Kotlin Core 라이브러리
    implementation(project(":libs:kotlin-core"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Jackson Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
EOF

# 6. 메인 Application.kt 생성
cat <<EOF > "$MAIN_DIR/${CLASS_NAME}.kt"
package ${FULL_PACKAGE}

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ${CLASS_NAME}

fun main(args: Array<String>) {
    runApplication<${CLASS_NAME}>(*args)
}
EOF

# 7. 테스트 ApplicationTests.kt 생성
cat <<EOF > "$TEST_DIR/${TEST_CLASS_NAME}.kt"
package ${FULL_PACKAGE}

import org.junit.jupiter.api.Test

class ${TEST_CLASS_NAME} {

    @Test
    fun contextLoads() {
    }
}
EOF

# 8. application.yml 생성
cat <<EOF > "$RES_DIR/application.yml"
server:
  port: 0

spring:
  application:
    name: $MODULE_NAME

  datasource:
    url: jdbc:h2:mem:${PACKAGE_SUFFIX}db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true
      path: /h2-console

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
EOF

# 9. settings.gradle에 include 추가
SETTINGS_FILE="settings.gradle"
INCLUDE_LINE="include 'apps:kotlin:${MODULE_NAME}'"

if grep -Fxq "$INCLUDE_LINE" "$SETTINGS_FILE"; then
    echo "ℹ️ settings.gradle에 이미 등록되어 있습니다."
else
    # Kotlin 서비스 섹션 뒤에 추가
    if grep -q "// Kotlin 전용 서비스" "$SETTINGS_FILE"; then
        # Kotlin 섹션의 마지막 include 라인 뒤에 추가
        LAST_KOTLIN_LINE=$(grep -n "include 'apps:kotlin:" "$SETTINGS_FILE" | tail -1 | cut -d: -f1)
        if [ -n "$LAST_KOTLIN_LINE" ]; then
            sed -i "${LAST_KOTLIN_LINE}a\\${INCLUDE_LINE}" "$SETTINGS_FILE"
        else
            echo "$INCLUDE_LINE" >> "$SETTINGS_FILE"
        fi
    else
        echo "" >> "$SETTINGS_FILE"
        echo "$INCLUDE_LINE" >> "$SETTINGS_FILE"
    fi
    echo "✅ settings.gradle 등록 완료: $INCLUDE_LINE"
fi

echo "🎉 [${MODULE_NAME}] Kotlin 모듈 생성 완료! Gradle Refresh를 실행해주세요."
