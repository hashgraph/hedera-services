module com.hedera.node.app.service.consensus.impl.test {
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.consensus.impl;
    requires org.junit.jupiter.api;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires org.mockito.junit.jupiter;
    requires org.mockito;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;

    opens com.hedera.node.app.service.consensus.impl.test to
            org.junit.platform.commons;
}
