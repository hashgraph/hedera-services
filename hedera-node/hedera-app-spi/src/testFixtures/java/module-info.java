module hedera.services.hedera.node.hedera.app.spi.testFixtures {
    exports com.hedera.node.app.spi.fixtures.state;

    requires com.hedera.node.app.spi;
    requires static com.github.spotbugs.annotations;
    requires org.assertj.core;
    requires com.swirlds.common;
}
