import com.hedera.node.app.service.contract.impl.ContractServiceImpl;

module com.hedera.node.app.service.contract.impl {
    requires transitive com.hedera.node.app.service.contract;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.token;
    requires com.hedera.pbj.runtime;
    requires com.github.spotbugs.annotations;
    requires com.swirlds.jasperdb;

    provides com.hedera.node.app.service.contract.ContractService with
            ContractServiceImpl;

    exports com.hedera.node.app.service.contract.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.handlers;
}
