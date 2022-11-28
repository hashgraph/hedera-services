module com.hedera.node.app.spi {
    requires static jsr305;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.spi;
    exports com.hedera.node.app.spi.state;
    exports com.hedera.node.app.spi.key;
    exports com.hedera.node.app.spi.meta;
}
