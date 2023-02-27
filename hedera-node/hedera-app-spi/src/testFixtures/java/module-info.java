module com.hedera.node.app.spi.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.state;

    requires com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires org.assertj.core;
    requires com.github.spotbugs.annotations;
}
