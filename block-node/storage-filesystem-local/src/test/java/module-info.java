module com.hedera.storage.filesystem.local.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.storage.filesystem.local.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.filesystem.local;
    requires org.junit.jupiter.api;
}
