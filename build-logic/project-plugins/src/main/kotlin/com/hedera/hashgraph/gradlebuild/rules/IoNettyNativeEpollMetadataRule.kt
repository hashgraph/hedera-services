/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

/**
 * Configures native Jars of 'io.netty.native.epoll' so that they can be selected by 'capability'.
 * https://docs.gradle.org/current/userguide/component_metadata_rules.html#making_different_flavors_of_a_library_available_through_capabilities
 */
@CacheableRule
abstract class IoNettyNativeEpollMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        val name = context.details.id.name
        val version = context.details.id.version
        listOf("linux-x86_64", "linux-aarch_64").forEach { nativeVariant ->
            context.details.addVariant(nativeVariant, "runtime") {
                withCapabilities {
                    removeCapability("io.netty", "netty-transport-native-epoll")
                    addCapability("io.netty", "netty-transport-native-epoll-$nativeVariant", version)
                }
                withFiles {
                    removeAllFiles()
                    addFile("$name-$version-$nativeVariant.jar")
                }
            }
        }
    }
}
