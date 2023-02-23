import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.contract.impl {
    requires com.hedera.node.app.service.contract;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.node.app.service.mono;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.evm;
    requires dagger;
    requires javax.inject;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.contract.impl;
    exports com.hedera.node.app.service.contract.impl.handlers;
    exports com.hedera.node.app.service.contract.impl.components;

    provides ServiceFactory with
            com.hedera.node.app.service.contract.impl.ContractServiceFactory;
}
