module hedera.services.test.clients {
    requires com.hedera.node.app.hapi.fees;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.hapi;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.services.cli;
    requires com.hedera.node.app.service.mono;
    requires org.junit.jupiter.api;
    requires org.apache.logging.log4j;
    requires info.picocli;
    requires com.google.common;
    requires com.google.protobuf;
    requires com.swirlds.config;
    requires org.apache.commons.lang3;
    requires org.apache.commons.io;
    requires org.bouncycastle.util;
    requires org.bouncycastle.provider;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires tuweni.bytes;
    requires io.grpc;
    requires grpc.netty;
    requires org.jetbrains.annotations;
    requires static com.github.spotbugs.annotations;
}
