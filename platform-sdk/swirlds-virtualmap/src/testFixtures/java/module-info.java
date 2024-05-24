module com.swirlds.virtualmap.test.fixtures {
    exports com.swirlds.virtualmap.test.fixtures;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.metrics.api;
    requires transitive org.junit.jupiter.api;
    requires com.swirlds.merkledb;
    requires com.swirlds.virtualmap;
}
