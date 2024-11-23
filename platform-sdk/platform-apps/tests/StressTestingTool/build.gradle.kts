// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.module.application") }

application.mainClass = "com.swirlds.demo.stress.StressTestingToolMain"

mainModuleInfo { annotationProcessor("com.swirlds.config.processor") }
