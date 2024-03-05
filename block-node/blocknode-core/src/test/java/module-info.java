module com.hedera.storage.blocknode.core.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.core.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.blocknode.core;
    requires com.hedera.storage.blocknode.core.spi;
    requires com.hedera.storage.blocknode.filesystem.api;
    requires com.hedera.storage.blocknode.grpc.api;
    requires com.hedera.storage.blocknode.state;
    requires com.hedera.storage.hapi;
    requires com.swirlds.platform.core;
    requires org.junit.jupiter.api;
}
