plugins {
	java
	id("org.springframework.boot") version "3.5.13"
	id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.gmail.alexei28"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val openapiVersion = "2.6.0"

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$openapiVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Format code
spotless {

    java {
        target("src/**/*.java")
        googleJavaFormat("1.17.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        formatAnnotations()
    }

    kotlin {
        target("src/**/*.kt")
        ktlint("1.3.1")
        trimTrailingWhitespace()
        endWithNewline()
    }

    // Блок для файлов сборки
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.3.1")
    }

    yaml {
        target("**/*.yml", "**/*.yaml")
        // jackson()
        trimTrailingWhitespace()
        endWithNewline()
    }

    json {
        target("**/*.json")
        targetExclude("**/build/**")
        gson().indentWithSpaces(2)
    }

    format("markdown") {
        target("**/*.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

