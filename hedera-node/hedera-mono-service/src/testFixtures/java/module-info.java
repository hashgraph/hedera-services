module com.hedera.node.app.service.mono.testFixtures {
    exports com.hedera.test.utils;
    exports com.hedera.test.factories.txns;
    exports com.hedera.test.factories.keys;
    exports com.hedera.test.mocks;
    exports com.hedera.test.factories.scenarios;

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
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.spi;
}
