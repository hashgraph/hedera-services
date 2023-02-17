module com.hedera.node.app.spi {
    requires transitive com.hedera.hashgraph.protobuf.java.api;
    requires static transitive com.github.spotbugs.annotations;
    requires com.swirlds.common;
    requires com.swirlds.config;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
    exports com.hedera.node.app.spi.numbers;
    exports com.hedera.node.app.spi.workflows;

    opens com.hedera.node.app.spi to
            com.hedera.node.app.spi.test,
            com.hedera.node.app.service.mono.testFixtures;
}
