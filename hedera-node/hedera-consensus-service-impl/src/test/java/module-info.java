module com.hedera.node.app.service.consensus.impl.test {
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.consensus.impl;
    requires org.junit.jupiter.api;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires com.hedera.node.app.service.mono;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires org.assertj.core;
    requires com.google.protobuf;

    opens com.hedera.node.app.service.consensus.impl.test to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.consensus.impl.handlers.test to
            org.junit.platform.commons;
}
