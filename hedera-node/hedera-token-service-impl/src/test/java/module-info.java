module com.hedera.node.app.service.token.impl.test {
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app.service.mono.test.fixtures;
    requires com.hedera.node.app.service.token.impl;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.github.spotbugs.annotations;
    requires com.google.protobuf;
    requires com.swirlds.common;
    requires com.swirlds.merkle;
    requires org.apache.commons.lang3;
    requires org.assertj.core;
    requires org.hamcrest;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires org.mockito;

    opens com.hedera.node.app.service.token.impl.test.util to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test.codec to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.handlers to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.records to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.config to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.validators to
            org.junit.platform.commons,
            org.mockito;
}
