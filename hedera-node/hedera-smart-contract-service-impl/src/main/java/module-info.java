import com.hedera.node.app.service.contract.impl.ContractServiceImpl;

module com.hedera.node.app.service.contract.impl {
    requires com.hedera.node.app.service.contract;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.contract.ContractService with
            ContractServiceImpl;

    exports com.hedera.node.app.service.contract.impl to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.handlers;
}
