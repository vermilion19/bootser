plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// 라이브러리 모듈 설정
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")

    // Jackson Kotlin Module
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
