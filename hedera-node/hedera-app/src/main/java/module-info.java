module com.hedera.node.app {
    requires io.helidon.grpc.core;
    requires io.helidon.grpc.server;
    requires com.swirlds.common;
    requires com.swirlds.merkle;
    requires com.swirlds.jasperdb;
    requires com.swirlds.virtualmap;
    requires org.slf4j;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires grpc.stub;
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
    requires dagger;
    requires javax.inject;
    requires com.swirlds.platform;
    requires org.apache.logging.log4j;
    requires com.google.common;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.hapi.fees;

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
}
