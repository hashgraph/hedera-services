module com.hedera.node.app.service.admin {
    exports com.hedera.node.app.service.admin;

    uses com.hedera.node.app.service.admin.FreezeService;

    requires transitive com.hedera.node.app.spi;
    requires transitive org.slf4j;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
