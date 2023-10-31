module com.hedera.node.app.test.fixtures {
    exports com.hedera.node.app.fixtures.state;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.app;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.config.api;
    requires org.assertj.core;
    requires static com.github.spotbugs.annotations;
}
