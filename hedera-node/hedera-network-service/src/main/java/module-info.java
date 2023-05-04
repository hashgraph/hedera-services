module com.hedera.node.app.service.network {
    exports com.hedera.node.app.service.network;

    uses com.hedera.node.app.service.network.NetworkService;

    requires transitive com.hedera.node.app.spi;
    requires com.github.spotbugs.annotations;
}
