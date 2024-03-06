module com.hedera.storage.blocknode.filesystem.api.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.filesystem.api.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.blocknode.filesystem.api;
    requires org.junit.jupiter.api;
}
