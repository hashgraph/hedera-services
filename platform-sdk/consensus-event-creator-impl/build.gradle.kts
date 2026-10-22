// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.module.library")
    id("org.hiero.gradle.feature.publish-artifactregistry")
    id("org.hiero.gradle.feature.benchmark")
    id("org.hiero.gradle.feature.benchmark-tracking")
}

jmhModuleInfo {
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("org.hiero.consensus.benchmark.tools")
    requires("org.hiero.consensus.event.creator")
    requires("org.hiero.consensus.event.creator.impl")
    requires("org.hiero.consensus.fakes")
    requires("org.hiero.consensus.hashgraph")
    requires("org.hiero.consensus.hashgraph.impl")
    requires("org.hiero.consensus.metrics")
    requires("org.hiero.consensus.model.test.fixtures")
    requires("org.hiero.consensus.utility")
    requires("org.hiero.consensus.utility.test.fixtures")
    requires("jmh.core")
}

jmh {
    // the benchmarks that are tracked; -PjmhTests=<pattern> runs others instead
    includes.convention(listOf("EventCreationBenchmark"))
}

description = "Default Consensus Event Creator Implementation"

testModuleInfo {
    requires("com.hedera.node.hapi")
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("org.hiero.base.crypto.test.fixtures")
    requires("org.hiero.base.utility.test.fixtures")
    requires("org.hiero.consensus.fakes")
    requires("org.hiero.consensus.model.test.fixtures")
    requires("org.hiero.consensus.utility.test.fixtures")
    requires("org.hiero.junit.extensions")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")

    opensTo("org.hiero.junit.extensions")
}
