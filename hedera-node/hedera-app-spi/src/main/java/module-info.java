module com.hedera.node.app.spi {
    requires transitive com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires static transitive com.github.spotbugs.annotations;

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
