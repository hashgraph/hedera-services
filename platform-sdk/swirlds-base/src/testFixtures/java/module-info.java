open module com.swirlds.base.test.fixtures {
    exports com.swirlds.base.test.fixtures.time;

    requires transitive com.swirlds.base;
    requires transitive org.junit.jupiter.api;
    requires com.swirlds.common; // Should be removed in future
}
