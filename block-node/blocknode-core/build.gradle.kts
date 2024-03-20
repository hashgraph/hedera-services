/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.blocknode.conventions")
}

application { mainClass = "com.hedera.node.blocknode.core.BlockNodeMain" }

mainModuleInfo {
    runtimeOnly("com.swirlds.config.impl")
    runtimeOnly("grpc.netty")
    runtimeOnly("com.hedera.storage.blocknode.filesystem.local")
    runtimeOnly("com.hedera.storage.blocknode.filesystem.s3")
    runtimeOnly("grpc.stub")
    runtimeOnly("io.netty.transport.classes.epoll")
}

tasks.withType<JavaExec>().configureEach {
    if (name.endsWith("main()")) {
        notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
}
