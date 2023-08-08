/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

plugins {
    id("com.hedera.hashgraph.sdk.conventions")
    id("com.swirlds.platform.maven-publish")
    id("org.gradle.java-test-fixtures")
}

dependencies {
    javaModuleDependencies {
        runtimeOnly(project(":swirlds-config-impl"))

        testImplementation(testFixtures(project(":swirlds-base")))
        testImplementation(testFixtures(project(":swirlds-common")))
        testImplementation(testFixtures(project(":swirlds-config-api")))
        testImplementation(project(":swirlds-common-testing"))
        testImplementation(project(":swirlds-platform-core"))
        testImplementation(project(":swirlds-test-framework"))
        testImplementation(gav("org.apache.commons.io"))
        testImplementation(gav("org.assertj.core"))
        testImplementation(gav("awaitility"))
        testImplementation(gav("org.junit.jupiter.api"))
        testImplementation(gav("org.junit.jupiter.params"))
        testImplementation(gav("org.mockito"))
        testCompileOnly(gav("com.github.spotbugs.annotations"))
    }
}
