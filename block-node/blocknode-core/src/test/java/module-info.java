module com.hedera.node.blocknode.core.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.core.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.node.blocknode.core;
    requires com.swirlds.platform.core;
    requires org.junit.jupiter.api;
    requires com.hedera.node.hapi;
    requires com.hedera.node.blocknode.state;
    requires com.hedera.node.blocknode.core.spi;
    requires com.hedera.node.blocknode.grpc.api;
    requires com.hedera.node.blocknode.filesystem.api;
}
