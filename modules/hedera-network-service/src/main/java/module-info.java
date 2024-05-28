module com.hedera.node.app.service.network {
    exports com.hedera.node.app.service.network;

    uses com.hedera.node.app.service.network.NetworkService;

    requires transitive com.hedera.node.app.spi;
    requires transitive org.slf4j;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
