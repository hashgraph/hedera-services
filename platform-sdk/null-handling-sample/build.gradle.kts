import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java-library")
    id("net.ltgt.errorprone") version "4.0.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories { gradlePluginPortal() }

dependencies {
    errorprone("com.uber.nullaway:nullaway:0.11.1")
    api("org.jspecify:jspecify:1.0.0")
    errorprone("com.google.errorprone:error_prone_core:2.29.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.sample")
    }
}