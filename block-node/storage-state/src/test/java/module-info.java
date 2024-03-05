module com.hedera.storage.state.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.storage.state.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.state;
    requires org.junit.jupiter.api;
}
