module com.hedera.node.app.spi {
    requires transitive com.hedera.hashgraph.protobuf.java.api;
    requires static transitive com.github.spotbugs.annotations;
    requires com.swirlds.common;
    requires transitive com.swirlds.config;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
    exports com.hedera.node.app.spi.numbers;
    exports com.hedera.node.app.spi.workflows;
    exports com.hedera.node.app.spi.service;

    opens com.hedera.node.app.spi to
            com.hedera.node.app.spi.test,
            com.hedera.node.app.service.mono.testFixtures;
    opens com.hedera.node.app.spi.service to
            com.hedera.node.app.service.mono.testFixtures,
            com.hedera.node.app.spi.test;
}
