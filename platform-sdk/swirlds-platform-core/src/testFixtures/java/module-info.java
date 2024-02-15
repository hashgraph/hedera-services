open module com.swirlds.platform.core.test.fixtures {
    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform.core;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires com.swirlds.logging;
    requires com.swirlds.metrics.api;
    requires java.desktop;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires static com.github.spotbugs.annotations;

    exports com.swirlds.platform.test.fixtures.stream;
    exports com.swirlds.platform.test.fixtures.event;
    exports com.swirlds.platform.test.fixtures.event.source;
    exports com.swirlds.platform.test.fixtures.event.generator;
    exports com.swirlds.platform.test.fixtures.state;
    exports com.swirlds.platform.test.fixtures.addressbook;
}
