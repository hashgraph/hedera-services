module com.hedera.node.app.service.contract.impl {
    requires com.hedera.node.app.service.contract;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.contract.ContractService with
            com.hedera.node.app.service.contract.impl.StandardContractService;

    exports com.hedera.node.app.service.contract.impl to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.handlers;
}
