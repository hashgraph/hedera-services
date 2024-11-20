/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
    id("com.hedera.gradle.services")
    id("com.hedera.gradle.services-publish")
    id("com.hedera.gradle.feature.test-fixtures")
}

description = "Hedera Application - SPI"

testModuleInfo {
    requires("com.hedera.node.app.spi")
    requires("com.swirlds.state.api.test.fixtures")
    requires("org.apache.commons.lang3")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    runtimeOnly("org.mockito")
    runtimeOnly("org.mockito.junit.jupiter")
    requiresStatic("com.github.spotbugs.annotations")
}
