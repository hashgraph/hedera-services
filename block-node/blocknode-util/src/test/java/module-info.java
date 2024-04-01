module com.hedera.storage.blocknode.util.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.util.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.blocknode.util;
    requires org.junit.jupiter.api;
}
