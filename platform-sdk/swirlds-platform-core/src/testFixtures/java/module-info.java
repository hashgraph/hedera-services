open module com.swirlds.platform.core.test.fixtures {
    requires com.swirlds.common;
    requires com.swirlds.common.test.fixtures;
    requires com.swirlds.platform.core;
    requires org.junit.jupiter.api;
    requires com.swirlds.test.framework;
    requires static com.github.spotbugs.annotations;

    exports com.swirlds.platform.test.fixtures.stream;
    exports com.swirlds.platform.test.fixtures.event;
    exports com.swirlds.platform.test.fixtures.event.source;
    exports com.swirlds.platform.test.fixtures.event.generator;
    exports com.swirlds.platform.test.fixtures.state;
}
