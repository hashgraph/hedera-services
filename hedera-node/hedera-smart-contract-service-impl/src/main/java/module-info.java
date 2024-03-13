module com.hedera.node.app.service.contract.impl {
    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.contract;
    requires transitive com.hedera.node.app.service.file;
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.token;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive dagger;
    requires transitive headlong;
    requires transitive javax.inject;
    requires transitive org.apache.logging.log4j;
    requires transitive org.hyperledger.besu.datatypes;
    requires transitive org.hyperledger.besu.evm;
    requires transitive tuweni.bytes;
    requires transitive tuweni.units;
    requires com.hedera.node.app.service.evm;
    requires com.github.benmanes.caffeine;
    requires com.google.common;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires org.bouncycastle.provider;
    requires static com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    exports com.hedera.node.app.service.contract.impl;
    exports com.hedera.node.app.service.contract.impl.exec.scope;
    exports com.hedera.node.app.service.contract.impl.records;
    exports com.hedera.node.app.service.contract.impl.handlers;
    exports com.hedera.node.app.service.contract.impl.hevm;
    exports com.hedera.node.app.service.contract.impl.state to
            com.hedera.node.services.cli,
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
            com.hedera.node.app.service.contract.impl.test,
            com.hedera.node.app.service.contract.impl.test.exec.scope;
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

    opens com.hedera.node.app.service.contract.impl.exec to
            com.hedera.node.app.service.contract.impl.test;

    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;
}
