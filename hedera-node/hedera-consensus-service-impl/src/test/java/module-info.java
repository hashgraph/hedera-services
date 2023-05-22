open module com.hedera.node.app.service.consensus.impl.test {
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.service.consensus.impl;
    requires com.hedera.node.app.service.mono.test.fixtures;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.hedera.node.app;
    requires com.github.spotbugs.annotations;
    requires com.google.protobuf;
    requires com.swirlds.common;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires org.mockito;
}
