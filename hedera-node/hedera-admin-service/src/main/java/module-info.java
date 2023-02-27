module com.hedera.node.app.service.admin {
    exports com.hedera.node.app.service.admin;
    exports com.hedera.node.app.service.admin.handlers;

    requires transitive com.hedera.node.app.spi;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
