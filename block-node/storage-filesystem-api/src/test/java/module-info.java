module com.hedera.storage.filesystem.api.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.storage.filesystem.api.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.filesystem.api;
    requires org.junit.jupiter.api;
}
