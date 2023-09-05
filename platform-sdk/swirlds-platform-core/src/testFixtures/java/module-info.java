open module com.swirlds.platform.core.test.fixtures {
    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform.core;
    requires com.swirlds.test.framework;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;

    exports com.swirlds.platform.test.fixtures.stream;
    exports com.swirlds.platform.test.fixtures.event;
    exports com.swirlds.platform.test.fixtures.event.source;
    exports com.swirlds.platform.test.fixtures.event.generator;
    exports com.swirlds.platform.test.fixtures.state;
}
