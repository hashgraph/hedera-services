import com.hedera.node.app.service.contract.impl.ContractServiceImpl;

module com.hedera.node.app.service.contract.impl {
    requires transitive com.hedera.node.app.service.contract;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.token;
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires tuweni.bytes;
    requires tuweni.units;
    requires com.hedera.node.config;
    requires com.hedera.pbj.runtime;
    requires com.github.spotbugs.annotations;
    requires com.swirlds.jasperdb;
    requires org.bouncycastle.provider;

    provides com.hedera.node.app.service.contract.ContractService with
            ContractServiceImpl;

    exports com.hedera.node.app.service.contract.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.handlers;
    exports com.hedera.node.app.service.contract.impl.state to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.utils to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.infra to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.v030 to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.utils to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.failure to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec;
    exports com.hedera.node.app.service.contract.impl.exec.operations;
    exports com.hedera.node.app.service.contract.impl.exec.v034;
    exports com.hedera.node.app.service.contract.impl.exec.v038;
}
