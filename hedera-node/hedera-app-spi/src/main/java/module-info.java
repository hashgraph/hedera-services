module com.hedera.node.app.spi {
    requires jsr305;
    requires com.hedera.hashgraph.protobuf.java.api;
    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
}
