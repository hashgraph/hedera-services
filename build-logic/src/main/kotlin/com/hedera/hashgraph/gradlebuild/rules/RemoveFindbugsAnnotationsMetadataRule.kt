package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

abstract class RemoveFindbugsAnnotationsMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                // 'findbugs' annotations are not used and cause split package with 'javax.annotation-api'
                removeAll { it.group == "com.google.code.findbugs" }
            }
        }
    }
}