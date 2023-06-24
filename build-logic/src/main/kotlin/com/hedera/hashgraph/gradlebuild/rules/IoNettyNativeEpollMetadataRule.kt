package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

abstract class IoNettyNativeEpollMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        val name = context.details.id.name
        val version = context.details.id.version
        context.details.allVariants {
            withFiles {
                // Always pick 'linux-x86_64' and 'linux-aarch_64' by default
                removeAllFiles()
                addFile("$name-$version-linux-x86_64.jar")
                addFile("$name-$version-linux-aarch_64.jar")
            }
        }
    }
}
