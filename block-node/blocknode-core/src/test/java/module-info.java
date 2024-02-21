module com.hedera.node.blocknode.core.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.core.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires static com.hedera.node.blocknode.core.spi;
    requires static com.hedera.node.blocknode.core;
    requires static com.hedera.node.blocknode.filesystem.api;
    requires static com.hedera.node.blocknode.grpc.api;
    requires static com.hedera.node.blocknode.state;
    requires static com.hedera.node.hapi;
    requires com.swirlds.platform.core;
    requires org.junit.jupiter.api;
}
