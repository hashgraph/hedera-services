package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

abstract class IoGrpcDependencyMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.name == "grpc-api" }
                removeAll { it.name == "grpc-context" }
                removeAll { it.name == "grpc-core" }
                add("io.helidon.grpc:io.grpc")
            }
        }
    }
}
