import com.hedera.node.app.service.contract.impl.ContractServiceImpl;

module com.hedera.node.app.service.contract.impl {
    requires transitive com.hedera.node.app.service.contract;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires transitive org.hyperledger.besu.datatypes;
    requires transitive org.hyperledger.besu.evm;
    requires transitive tuweni.bytes;
    requires transitive tuweni.units;
    requires com.swirlds.common;
    requires com.swirlds.config;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.token;
    requires com.swirlds.jasperdb;
    requires static com.github.spotbugs.annotations;
    requires org.bouncycastle.provider;

    provides com.hedera.node.app.service.contract.ContractService with
            ContractServiceImpl;

    exports com.hedera.node.app.service.contract.impl;
    exports com.hedera.node.app.service.contract.impl.handlers;
    exports com.hedera.node.app.service.contract.impl.hevm;
    exports com.hedera.node.app.service.contract.impl.state to
            com.hedera.node.app.service.contract.impl.test,
            com.hedera.node.app;

    opens com.hedera.node.app.service.contract.impl.utils to
            com.hedera.node.app.service.contract.impl.test;

    exports com.hedera.node.app.service.contract.impl.infra to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.gas to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.v030 to
            com.hedera.node.app.service.contract.impl.test;

    opens com.hedera.node.app.service.contract.impl.exec.utils to
            com.hedera.node.app.service.contract.impl.test;

    exports com.hedera.node.app.service.contract.impl.exec.failure to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec;
    exports com.hedera.node.app.service.contract.impl.exec.operations to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.processors to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.v034 to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.v038 to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.utils;
    exports com.hedera.node.app.service.contract.impl.exec.utils;
}
