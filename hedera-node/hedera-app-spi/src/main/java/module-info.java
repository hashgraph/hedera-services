module com.hedera.node.app.spi {
    requires transitive com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.common;
    requires com.google.protobuf;
    requires com.swirlds.config;
    requires com.github.spotbugs.annotations;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
    exports com.hedera.node.app.spi.numbers;
    exports com.hedera.node.app.spi.workflows;
    exports com.hedera.node.app.spi.config;
    exports com.hedera.node.app.spi.records;
    exports com.hedera.node.app.spi.validation;
    exports com.hedera.node.app.spi.accounts;
    exports com.hedera.node.app.spi.info;
}
