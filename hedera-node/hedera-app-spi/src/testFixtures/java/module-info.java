module com.hedera.node.app.spi.test.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.state;
    exports com.hedera.node.app.spi.fixtures.workflows;
    exports com.hedera.node.app.spi.fixtures.util;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive org.apache.logging.log4j;
    requires transitive org.assertj.core;
    requires transitive org.junit.jupiter.api;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.common;
    requires org.apache.logging.log4j.core;

    // Temporarily needed until FakePreHandleContext can be removed
    requires static com.hedera.node.app.service.token;
    requires static com.github.spotbugs.annotations;
}
