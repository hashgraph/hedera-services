// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.module.library") }

description = "Hedera Schedule Service API"

testModuleInfo {
    requires("com.swirlds.state.api")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
}
