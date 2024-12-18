// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.module.library")
    id("org.hiero.gradle.feature.test-fixtures")
}

description = "Hedera Application - SPI"

testModuleInfo {
    requires("com.hedera.node.app.spi")
    requires("com.swirlds.state.api.test.fixtures")
    requires("org.apache.commons.lang3")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requiresStatic("com.github.spotbugs.annotations")
}
