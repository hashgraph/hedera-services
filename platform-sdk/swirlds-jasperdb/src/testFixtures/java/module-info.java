module com.swirlds.merkledb.test.fixtures {
    exports com.swirlds.merkledb.test.fixtures;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.virtualmap;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires com.swirlds.merkledb;
    requires java.management;
    requires jdk.management;
    requires org.junit.jupiter.api;
    requires org.mockito;
}
