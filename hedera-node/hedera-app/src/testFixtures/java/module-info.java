module com.hedera.node.app.test.fixtures {
    exports com.hedera.node.app.fixtures.state;

    requires transitive com.hedera.node.app.spi;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.hedera.node.app;
    requires com.github.spotbugs.annotations;
    requires com.swirlds.config;
}
