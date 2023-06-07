module com.hedera.node.app.spi {
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config;
    requires com.github.spotbugs.annotations;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.numbers;
    exports com.hedera.node.app.spi.workflows;
    exports com.hedera.node.app.spi.records;
    exports com.hedera.node.app.spi.signatures;
    exports com.hedera.node.app.spi.validation;
    exports com.hedera.node.app.spi.info;
    exports com.hedera.node.app.spi.meta.bni;
}
