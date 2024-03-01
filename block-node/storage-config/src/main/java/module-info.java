module com.hedera.storage.config {
    // Selectively export non-public packages to the test module.
    exports com.hedera.storage.config;
    exports com.hedera.storage.config.data;

    requires com.swirlds.config.extensions;
    requires static com.github.spotbugs.annotations;
}
