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

    opens com.hedera.node.app.service.contract.impl.test to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.contract.impl.test.handlers to
            org.junit.platform.commons;
}
