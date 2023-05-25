module com.hedera.node.app.spi.test.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.state;
    exports com.hedera.node.app.spi.fixtures.workflows;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config;
    requires transitive org.assertj.core;
    requires com.github.spotbugs.annotations;

    // Temporarily needed until FakePreHandleContext can be removed
    requires static com.hedera.node.app.service.token;
}
