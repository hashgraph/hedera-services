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
 * Removes annotation libraries for annotations that this project does not require
 * at runtime or compile time. These are typically annotations used by additional
 * code analysis tools that are not used by this project.
 */
@CacheableRule
abstract class RemoveAnnotationLibrariesMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.name == "animal-sniffer-annotations" }
                removeAll { it.name == "checker-qual" }
                removeAll { it.name == "error_prone_annotations" }
                removeAll { it.name == "j2objc-annotations" }
                removeAll { it.name == "listenablefuture" }
                removeAll { it.group == "com.google.android" && it.name == "annotations"}
                // 'findbugs' annotations are not used and cause split package with 'javax.annotation-api'
                removeAll { it.group == "com.google.code.findbugs" }
            }
        }
    }
}