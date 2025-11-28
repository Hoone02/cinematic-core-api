import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

repositories {
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.maven.apache.org/maven2/")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(libs.io.papermc.paper.paper.api)
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
    implementation(platform("net.kyori:adventure-bom:4.17.0"))
    compileOnly("net.kyori:adventure-text-serializer-ansi:4.17.0")
}

group = "com.github.Hoone02"
version = "1.0.0"
description = "CinematicCore"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.Hoone02"
            artifactId = "cinematic-core-api"  // 저장소 이름과 일치
            version = project.version.toString()

            // components["java"]는 이미 소스 JAR와 Javadoc JAR를 포함합니다
            from(components["java"])
        }
    }
}

// JitPack 배포를 위한 일반 jar (의존성 제외)
// java-library 플러그인이 이미 올바르게 설정하므로 추가 설정 불필요

// 로컬 개발/배포를 위한 fat jar (의존성 포함)
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    destinationDirectory.set(file("F:\\작업\\마인크래프트\\서버\\21.8 - 개발서버\\plugins"))
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
    // fatJar는 assemble에 포함하지 않음 (JitPack 배포 시 불필요)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}