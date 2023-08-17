package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

abstract class IoPrometheusSimpleclientMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.name == "simpleclient_tracer_otel" }
                removeAll { it.name == "simpleclient_tracer_otel_agent" }
            }
        }
    }
}