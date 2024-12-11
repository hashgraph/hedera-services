// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.module.application") }

application.mainClass = "com.swirlds.demo.consistency.ConsistencyTestingToolMain"

mainModuleInfo { annotationProcessor("com.swirlds.config.processor") }

testModuleInfo {
    requires("com.swirlds.common.test.fixtures")
    requires("org.junit.jupiter.api")
    requires("org.mockito")
}
