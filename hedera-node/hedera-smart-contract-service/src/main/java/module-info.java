module com.hedera.node.app.service.contract {
    exports com.hedera.node.app.service.contract;

    uses com.hedera.node.app.service.contract.ContractService;

    requires transitive com.hedera.node.app.spi;
    requires transitive org.slf4j;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
