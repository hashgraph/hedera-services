module com.hedera.node.app.test.fixtures {
    exports com.hedera.node.app.fixtures.state;

    requires transitive com.hedera.node.app.spi.test.fixtures;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.app;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.config.test.fixtures;
    requires com.hedera.node.config;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires com.swirlds.metrics.api;
    requires com.swirlds.platform.core;
    requires org.assertj.core;
    requires static transitive com.github.spotbugs.annotations;
}
