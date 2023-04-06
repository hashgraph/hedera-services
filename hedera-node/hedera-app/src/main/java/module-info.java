module com.hedera.node.app {
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.spi;
    requires com.hedera.node.app.service.admin;
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.contract;
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.network;
    requires com.hedera.node.app.service.scheduled;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.util;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app.service.admin.impl;
    requires com.hedera.node.app.service.consensus.impl;
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.file.impl;
    requires com.hedera.node.app.service.network.impl;
    requires com.hedera.node.app.service.schedule.impl;
    requires com.hedera.node.app.service.token.impl;
    requires com.hedera.node.app.service.util.impl;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.platform;
    requires com.swirlds.fchashmap;
    requires com.swirlds.config;
    requires com.swirlds.common;
    requires com.swirlds.merkle;
    requires com.swirlds.jasperdb;
    requires com.swirlds.virtualmap;
    requires io.helidon.grpc.core;
    requires io.helidon.grpc.server;
    requires grpc.stub;
    requires org.slf4j;
    requires dagger;
    requires javax.inject;
    requires org.apache.logging.log4j;
    requires org.apache.commons.lang3;
    requires com.google.common;
    requires com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.hapi.fees;
    requires com.hedera.node.hapi;
    requires java.logging;

    exports com.hedera.node.app to
            com.swirlds.platform;
    exports com.hedera.node.app.state to
            com.swirlds.common;
    exports com.hedera.node.app.state.merkle to
            com.swirlds.common;
    exports com.hedera.node.app.state.merkle.disk to
            com.swirlds.common;
    exports com.hedera.node.app.state.merkle.memory to
            com.swirlds.common;
    exports com.hedera.node.app.state.merkle.singleton to
            com.swirlds.common;
    exports com.hedera.node.app.authorization to
            com.swirlds.platform;
    exports com.hedera.node.app.state.merkle.adapters to
            com.swirlds.platform;
    exports com.hedera.node.app.fees to
            com.swirlds.platform;
    exports com.hedera.node.app.throttle to
            com.swirlds.platform;
}
