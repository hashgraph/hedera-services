/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.benchmark-conventions")
}

dependencies {
    javaModuleDependencies {
        jmhImplementation(project(":swirlds-common"))
        jmhImplementation(project(":swirlds-config-api"))
        jmhImplementation(project(":swirlds-platform-core"))
        jmhImplementation(testFixtures(project(":swirlds-common")))
        jmhImplementation(testFixtures(project(":swirlds-platform-core")))
        jmhImplementation(gav("jmh.core"))
        jmhImplementation(gav("org.apache.commons.lang3"))

        testImplementation(project(":swirlds-common-testing"))
        testImplementation(project(":swirlds-merkle"))
        testImplementation(project(":swirlds-sign-tool")) // should be removed in future
        testImplementation(testFixtures(project(":swirlds-base")))
        testImplementation(gav("awaitility"))
        testImplementation(gav("org.apache.commons.collections4"))
        testImplementation(gav("org.junit.jupiter.params"))
        testImplementation(gav("org.mockito.junit.jupiter"))
        testCompileOnly(gav("com.github.spotbugs.annotations"))
    }
}
