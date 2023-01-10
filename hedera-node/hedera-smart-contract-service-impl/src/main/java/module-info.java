import com.hedera.node.app.service.contract.impl.ContractServiceImpl;

module com.hedera.node.app.service.contract.impl {
    requires transitive com.hedera.node.app.service.contract;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.contract.ContractService with
            ContractServiceImpl;

    exports com.hedera.node.app.service.contract.impl to
            com.hedera.node.app.service.contract.impl.itest;
    exports com.hedera.node.app.service.contract.impl.handlers;
}
