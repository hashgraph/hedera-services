module com.hedera.storage.config.test {
    // Selectively export non-public packages to the test module.
    exports com.hedera.storage.config.test to
            org.junit.platform.commons;

    requires com.swirlds.platform.core;
    requires org.junit.jupiter.api;
}
