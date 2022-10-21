module com.hedera.services.test.clients {
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.github.docker.java.api;
    requires com.github.docker.java.transport;
    requires com.hedera.hashgraph.ethereumj.core;
    requires com.hedera.services.hapi.utils;
    requires com.hedera.services.hapi.fees;
    requires com.google.common;
    requires com.swirlds.common;
    requires info.picocli;
    requires io.grpc.api;
    requires io.grpc.netty;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.crypto;
    requires org.junit.jupiter.api;
    requires org.testcontainers;
    requires net.i2p.crypto.eddsa;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
}
