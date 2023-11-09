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
 * Replace all 'grpc' dependencies with a singe dependency to
 * 'io.helidon.grpc:io.grpc' which is a re-packaged Modular Jar
 * of all the 'grpc' libraries.
 */
@CacheableRule
abstract class IoGrpcDependencyMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.name == "grpc-api" }
                removeAll { it.name == "grpc-context" }
                removeAll { it.name == "grpc-core" }
                removeAll { it.name == "error_prone_annotations" }
                removeAll { it.group == "com.google.code.findbugs" }
                add("io.helidon.grpc:io.grpc")
            }
        }
    }
}
