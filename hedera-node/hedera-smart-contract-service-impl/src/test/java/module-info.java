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
    requires org.mockito;
    requires tuweni.units;

    opens com.hedera.node.app.service.contract.impl.test to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.state to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.utils to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.handlers to
            org.junit.platform.commons;
}
