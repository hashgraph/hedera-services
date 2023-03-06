open module com.hedera.node.app.service.consensus.impl.test {
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.consensus.impl;
    requires org.junit.jupiter.api;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires com.hedera.node.app.service.mono;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires org.assertj.core;
    requires com.google.protobuf;
    requires com.hedera.node.app.spi.fixtures;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.common;
    requires org.apache.commons.codec;
    requires com.hedera.node.hapi;
    requires com.hedera.hashgraph.pbj.runtime;
}
