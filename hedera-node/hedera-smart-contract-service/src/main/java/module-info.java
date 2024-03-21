module com.hedera.node.app.service.contract {
    exports com.hedera.node.app.service.contract;

    uses com.hedera.node.app.service.contract.ContractService;

    requires com.hedera.node.hapi;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires static transitive com.github.spotbugs.annotations;
}
