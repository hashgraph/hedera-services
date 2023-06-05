module com.hedera.node.app.service.contract.impl.test {
    requires com.hedera.node.app.service.contract;
    requires com.hedera.node.app.service.contract.impl;
    requires org.junit.jupiter.api;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires org.mockito;
    requires org.hamcrest;
    requires org.assertj.core;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.spi;
    requires com.hedera.node.app.spi.fixtures;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.service.token;
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires tuweni.bytes;
    requires tuweni.units;
    requires com.hedera.node.config.testfixtures;
    requires com.hedera.node.config;

    opens com.hedera.node.app.service.contract.impl.test to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.state to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.utils to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.handlers to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.operations to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.v034 to
            org.junit.platform.commons;
}
