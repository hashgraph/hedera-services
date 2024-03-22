module com.hedera.storage.blocknode.config.test {
    requires com.hedera.storage.blocknode.config;
    requires org.junit.jupiter.api;
    // Selectively export non-public packages to the test module.
    opens com.hedera.node.blocknode.config.test to
            org.junit.platform.commons;
}
