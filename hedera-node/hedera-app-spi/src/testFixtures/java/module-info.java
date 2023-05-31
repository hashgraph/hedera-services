module com.hedera.node.app.spi.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.state;
    exports com.hedera.node.app.spi.fixtures.workflows;
    exports com.hedera.node.app.spi.fixtures.util;

    requires com.hedera.node.app.spi;
    requires com.hedera.pbj.runtime;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires org.junit.jupiter.api;
    requires org.assertj.core;
    requires com.github.spotbugs.annotations;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    // Temporarily needed until FakePreHandleContext can be removed
    requires com.hedera.node.app.service.token;
    requires com.swirlds.config;
}
