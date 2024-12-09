/**
 * Provides the classes necessary to manage Hedera Smart Contract Service.
 */
module com.hedera.node.app.service.contract {
    exports com.hedera.node.app.service.contract;

    uses com.hedera.node.app.service.contract.ContractService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires com.hedera.node.hapi;
    requires static com.github.spotbugs.annotations;
}
