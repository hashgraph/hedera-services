/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradlex.javamodule.dependencies.initialization.JavaModulesExtension
import org.gradlex.javamodule.dependencies.initialization.RootPluginsExtension

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

plugins {
    id("com.gradle.develocity")
    id("org.gradlex.java-module-dependencies")
}

// Plugins that are global, but are applied to the "root project" instead of settings.
// by having this block here, we  do not require a "build.gradle.kts" in the repository roots.
configure<RootPluginsExtension> { id("com.hedera.gradle.root") }

// Enable Gradle Build Scan
develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
        termsOfUseAgree = "yes"
        publishing.onlyIf { false } // only publish with explicit '--scan'
    }
}

val isCiServer = System.getenv().containsKey("CI")
val gradleCacheUsername: String? = System.getenv("GRADLE_CACHE_USERNAME")
val gradleCachePassword: String? = System.getenv("GRADLE_CACHE_PASSWORD")
val gradleCacheAuthorized =
    (gradleCacheUsername?.isNotEmpty() ?: false) && (gradleCachePassword?.isNotEmpty() ?: false)

buildCache {
    remote<HttpBuildCache> {
        url = uri("https://cache.gradle.hedera.svcs.eng.swirldslabs.io/cache/")
        isPush = isCiServer && gradleCacheAuthorized

        isUseExpectContinue = true
        isEnabled = !gradle.startParameter.isOffline

        if (isCiServer && gradleCacheAuthorized) {
            credentials {
                username = gradleCacheUsername
                password = gradleCachePassword
            }
        }
    }
}

// Allow projects inside a build to be addressed by dependency coordinates notation.
// https://docs.gradle.org/current/userguide/composite_builds.html#included_build_declaring_substitutions
// Some functionality of the 'java-module-dependencies' plugin relies on this.
includeBuild(".")

configure<JavaModulesExtension> {
    // Project to aggregate code coverage data for the whole repository into one report
    module("gradle/reports")

    // "BOM" with versions of 3rd party dependencies
    versions("hedera-dependency-versions")
}
