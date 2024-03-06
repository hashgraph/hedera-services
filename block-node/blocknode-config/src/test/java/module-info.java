module com.hedera.storage.blocknode.config.test {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.config.test to
            org.junit.platform.commons;
}
