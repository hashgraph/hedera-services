module com.hedera.node.app.service.mono.testFixtures {
    exports com.hedera.test.factories.txns;
    requires org.junit.jupiter.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.swirlds.common;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.virtualmap;
    requires org.mockito;
    requires com.swirlds.merkle;
    requires net.i2p.crypto.eddsa;
    requires org.bouncycastle.provider;
    requires org.apache.commons.codec;
}
