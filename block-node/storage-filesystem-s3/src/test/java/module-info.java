module com.hedera.storage.filesystem.s3.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.storage.filesystem.s3.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.filesystem.s3;
    requires org.junit.jupiter.api;
}
