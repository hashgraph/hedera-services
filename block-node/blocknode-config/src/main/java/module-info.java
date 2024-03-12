module com.hedera.storage.blocknode.config {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.config;
    exports com.hedera.node.blocknode.config.data;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.config.impl;
    requires static com.github.spotbugs.annotations;
}
