open module com.hedera.node.app.xtest {
    requires com.hedera.node.app.hapi.fees;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.consensus.impl;
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.file.impl;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.network.admin.impl;
    requires com.hedera.node.app.service.schedule.impl;
    requires com.hedera.node.app.service.token.impl;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.util.impl;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.hedera.node.app.spi;
    requires com.hedera.node.app.test.fixtures;
    requires com.hedera.node.app;
    requires com.hedera.node.config.test.fixtures;
    requires com.hedera.node.config;
    requires com.hedera.node.hapi;
    requires com.github.spotbugs.annotations;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.test.framework;
    requires dagger;
    requires headlong;
    requires javax.inject;
    requires org.assertj.core;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.evm;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires org.mockito;
    requires tuweni.bytes;
    requires static java.compiler; // javax.annotation.processing.Generated
}
