module com.hedera.node.app.test.fixtures {
    exports com.hedera.node.app.fixtures.state;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.app;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.swirlds.config;
    requires static com.github.spotbugs.annotations;
}
