module com.hedera.storage.blocknode.core.test {
    // Open test packages to JUnit 5 and Mockito as required.
    opens com.hedera.node.blocknode.core.test to
            org.junit.platform.commons;

    // Require other modules needed for the unit tests to compile.
    requires com.hedera.storage.blocknode.core;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.engine;
    requires org.junit.jupiter;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.github.spotbugs.annotations;
    requires com.hedera.storage.blocknode.config;
}
