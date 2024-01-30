module com.hedera.node.test.clients {
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.config;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.github.docker.java.api;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.platform.core;
    requires com.swirlds.test.framework;
    requires grpc.netty;
    requires grpc.stub;
    requires io.netty.handler;
    requires java.net.http;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.internal.crypto;
    requires org.json;
    requires org.opentest4j;
    requires tuweni.units;
}
