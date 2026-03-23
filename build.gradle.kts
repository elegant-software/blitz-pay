plugins {
    alias(libs.plugins.skillsjars)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}

group = "com.elegant.software.blitzpay"
version = "0.2.2"
description = "BlitzPay"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val mavenProxyUrl = providers.gradleProperty("mavenProxyUrl").orNull
val skillsJarsRepositoryUrl = providers.gradleProperty("skillsJarsRepositoryUrl").orNull
val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()
val contractTestSourceSet = sourceSets.create("contractTest") {
    java.setSrcDirs(listOf("src/contractTest/kotlin"))
    resources.setSrcDirs(listOf("src/contractTest/resources"))
    compileClasspath += sourceSets["main"].output + configurations["testCompileClasspath"]
    runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
}

repositories {
    mavenLocal()

    if (mavenProxyUrl != null) {
        maven { url = uri(mavenProxyUrl) }
    } else {
        mavenCentral()
    }

    maven { url = uri("https://repo.spring.io/snapshot") }

    if (skillsJarsRepositoryUrl != null) {
        maven { url = uri(skillsJarsRepositoryUrl) }
    }
}

dependencies {
    if (providers.gradleProperty("includeSkillsJars").map(String::toBoolean).orElse(false).get()) {
        runtimeOnly(libs.dr.jskill)
    }
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation(libs.springdoc.openapi.starter.webflux.ui)
    // TrueLayer Java SDK
    implementation(libs.truelayer.java)
    implementation(libs.truelayer.signing) // official signing lib
    implementation(libs.kotlin.logging.jvm) //Idiomatic kotlin logging
    implementation(libs.nimbus.jose.jwt) // Required for signature verification
    // Mustang Project – EU-standard ZUGFeRD / Factur-X invoice generation
    implementation(libs.mustang.library)
    // Thymeleaf templating engine for invoice PDF rendering
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    // Flying Saucer – converts Thymeleaf-rendered HTML to PDF
    implementation(libs.flying.saucer.pdf)

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.kotlin)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${libs.versions.spring.modulith.get()}")
    }
}

configurations.named("contractTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("contractTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property", "-jvm-target=25")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("contractTest") {
    description = "Runs Boot 4-compatible contract tests."
    group = "verification"
    testClassesDirs = contractTestSourceSet.output.classesDirs
    classpath = contractTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("contractTest")
}
