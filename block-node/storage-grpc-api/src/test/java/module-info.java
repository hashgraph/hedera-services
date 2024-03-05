module com.hedera.storage.grpc.api.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.storage.core.grpc.api.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.grpc.api;
    requires org.junit.jupiter.api;
}
