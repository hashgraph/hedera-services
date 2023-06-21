module com.hedera.node.app.service.contract.impl.test {
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.mono.test.fixtures;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.google.protobuf;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.spi;
    requires com.hedera.pbj.runtime;
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires com.github.spotbugs.annotations;
    requires tuweni.bytes;
    requires tuweni.units;
    requires com.hedera.node.config;
    requires org.mockito;
    requires com.hedera.node.config.test.fixtures;

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
    opens com.hedera.node.app.service.contract.impl.test.exec to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.gas to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.v030 to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.v034 to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.v038 to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.exec.processors to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.hevm to
            org.junit.platform.commons;
}
