// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.module.application") }

application.mainClass = "com.swirlds.demo.iss.ISSTestingToolMain"

mainModuleInfo { annotationProcessor("com.swirlds.config.processor") }
