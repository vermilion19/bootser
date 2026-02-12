plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Kotlin Core 라이브러리 (kotlin-reflect, kotlin-stdlib, jackson-module-kotlin, test 포함)
    implementation(project(":libs:kotlin-core"))

    // Spring WebFlux (Reactor Netty 기반 - 10만 동시 접속 대응)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Redis Reactive (Pub/Sub 기반 수평 확장)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Actuator + Prometheus 메트릭
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 부하 테스터 별도 실행 가능 JAR
tasks.register<Jar>("loadTesterJar") {
    archiveBaseName.set("load-tester")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.booster.kotlin.chattingservice.test.LoadTesterKt"
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
