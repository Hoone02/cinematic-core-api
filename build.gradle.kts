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

            from(components["java"])

            // 소스 JAR 포함
            artifact(tasks["sourcesJar"])
            // Javadoc JAR 포함
            artifact(tasks["javadocJar"])
        }
    }
}

// JitPack 배포를 위한 일반 jar (의존성 제외)
tasks.jar {
    archiveClassifier.set("")
    // JitPack은 일반 jar를 사용하므로 의존성 포함하지 않음
}

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