module com.swirlds.virtualmap.test.fixtures {
    exports com.swirlds.merkledb.test.fixtures;
    exports com.swirlds.virtualmap.test.fixtures;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.virtualmap;
    requires transitive org.junit.jupiter.api;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires java.management;
    requires jdk.management;
    requires org.mockito;
}
