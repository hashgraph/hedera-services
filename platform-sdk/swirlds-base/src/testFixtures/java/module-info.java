open module com.swirlds.base.test.fixtures {
    exports com.swirlds.base.test.fixtures.time;

    requires transitive com.swirlds.base;
    requires com.swirlds.common; // Should be removed in future
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
    requires transitive jakarta.inject;
}
