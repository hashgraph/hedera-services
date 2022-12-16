module com.hedera.node.app.spi {
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;

    // Swirlds requirements are due to the API in StateBuilder. As the hashgraph platform
    // modules are refactored, it may be that we can reduce the number of required imports
    requires com.swirlds.common;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
    exports com.hedera.node.app.spi.numbers;
}
