module com.hedera.node.app.spi.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.meta;
    exports com.hedera.node.app.spi.fixtures.state;

    requires com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
    requires org.assertj.core;
}
