module com.hedera.node.app.test.fixtures {
    exports com.hedera.node.app.fixtures.state;

    requires transitive com.hedera.node.app.spi.test.fixtures;
    requires transitive com.hedera.node.app;
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.platform.core.test.fixtures;
    requires transitive com.swirlds.platform.core;
    requires transitive com.swirlds.state.api;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.spi;
    requires com.hedera.node.config.test.fixtures;
    requires com.hedera.node.config;
    requires com.swirlds.base;
    requires com.swirlds.config.extensions.test.fixtures;
    requires com.swirlds.metrics.api;
    requires com.hedera.pbj.runtime;
    requires com.sun.jna;
    requires org.apache.logging.log4j;
    requires org.assertj.core;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.nativelib.secp256k1;
    requires static com.github.spotbugs.annotations;
}
