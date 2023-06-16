module com.hedera.node.app.service.contract {
    exports com.hedera.node.app.service.contract;

    uses com.hedera.node.app.service.contract.ContractService;

    requires transitive com.hedera.node.app.spi;
    requires static com.github.spotbugs.annotations;
}
