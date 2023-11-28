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
            com.hedera.node.app.service.contract.impl.test,
            com.hedera.node.app,
            com.hedera.node.app.xtest;

    opens com.hedera.node.app.service.contract.impl.utils to
            com.hedera.node.app.service.contract.impl.test;

    exports com.hedera.node.app.service.contract.impl.infra to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.gas to
            com.hedera.node.app.service.contract.impl.test,
            com.hedera.node.app.xtest;
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
            com.hedera.node.app.service.contract.impl.test,
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.v034 to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.exec.v038 to
            com.hedera.node.app.service.contract.impl.test;
    exports com.hedera.node.app.service.contract.impl.utils;
    exports com.hedera.node.app.service.contract.impl.exec.utils;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.customfees to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultfreezestatus to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.defaultkycstatus to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.delete to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.fungibletokeninfo to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isfrozen to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.iskyc to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.istoken to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.pauses to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenexpiry to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokeninfo to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokentype to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update to
            com.hedera.node.app.xtest;
    exports com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe to
            com.hedera.node.app.xtest;

    opens com.hedera.node.app.service.contract.impl.exec to
            com.hedera.node.app.service.contract.impl.test;
}
