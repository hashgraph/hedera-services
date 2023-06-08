package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

abstract class JavaxAnnotationMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                // because of the split package, 'javax.annotation' libraries always need to go together
                add("javax.annotation:javax.annotation-api:1.3.2")
            }
        }
    }
}