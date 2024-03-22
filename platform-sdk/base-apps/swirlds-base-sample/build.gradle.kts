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
    id("application")
    id("com.hedera.hashgraph.sdk.conventions")
}

mainModuleInfo {
    annotationProcessor("com.swirlds.config.processor")
    annotationProcessor("com.google.auto.service.processor")
    runtimeOnly("com.swirlds.config.impl")
    runtimeOnly("loki.log4j2")
}

application.mainClass.set("com.swirlds.base.sample.BaseSampleMain")

// IntelliJ uses adhoc-created JavaExec tasks when running a 'main()' method.
tasks.withType<JavaExec> {
    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
    if (name.endsWith("main()")) {
        notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
}
