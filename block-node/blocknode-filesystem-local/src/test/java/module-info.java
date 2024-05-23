module com.hedera.storage.blocknode.filesystem.local.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.filesystem.local.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.blocknode.filesystem.local;
    requires org.junit.jupiter.api;
}
