/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.5.0.2730")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.15.0")
    implementation("gradle.plugin.com.google.cloud.artifactregistry:artifactregistry-gradle-plugin:2.1.5")
    implementation("com.gorylenko.gradle-git-properties:gradle-git-properties:2.4.1")
    implementation("gradle.plugin.lazy.zoo.gradle:git-data-plugin:1.2.2")
    implementation("net.swiftzer.semver:semver:1.1.2")
    implementation("org.owasp:dependency-check-gradle:8.1.0")
    implementation("org.gradlex:extra-java-module-info:1.3")
    implementation("me.champeau.jmh:jmh-gradle-plugin:0.6.6")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.19")
    implementation("com.adarshr:gradle-test-logger-plugin:3.2.0")
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}
