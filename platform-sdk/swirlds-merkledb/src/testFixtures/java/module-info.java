module com.swirlds.merkledb.test.fixtures {
    exports com.swirlds.merkledb.test.fixtures;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.virtualmap;
    requires transitive org.apache.logging.log4j.core;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires com.swirlds.merkledb;
    requires java.management;
    requires jdk.management;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires org.mockito;
}
