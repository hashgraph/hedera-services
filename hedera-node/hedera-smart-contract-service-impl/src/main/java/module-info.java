import com.hedera.node.app.service.contract.impl.ContractServiceImpl;

module com.hedera.node.app.service.contract.impl {
    requires com.hedera.node.app.service.contract;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.node.app.service.mono;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.evm;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;
    requires dagger;
    requires javax.inject;

    provides com.hedera.node.app.service.contract.ContractService with
            ContractServiceImpl;

    exports com.hedera.node.app.service.contract.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.handlers;
    exports com.hedera.node.app.service.contract.impl.components;
}
