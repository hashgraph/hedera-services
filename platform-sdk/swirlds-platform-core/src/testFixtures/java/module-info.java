open module com.swirlds.platform.core.test.fixtures {
    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform.core;
    requires com.swirlds.logging;
    requires com.swirlds.test.framework;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires org.junit.jupiter.params;
    requires com.swirlds.base.test.fixtures;
    requires org.mockito.junit.jupiter;

    requires static com.github.spotbugs.annotations;

    exports com.swirlds.platform.test.fixtures.stream;
    exports com.swirlds.platform.test.fixtures.event;
    exports com.swirlds.platform.test.fixtures.event.source;
    exports com.swirlds.platform.test.fixtures.event.generator;
    exports com.swirlds.platform.test.fixtures.state;
    exports com.swirlds.platform.test.fixtures.consensus;
    exports com.swirlds.platform.test.fixtures.network.communication;
}
