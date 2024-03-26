import com.hedera.node.app.config.ServicesConfigExtension;
import com.swirlds.config.api.ConfigurationExtension;

module com.hedera.node.app {
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.consensus.impl;
    requires transitive com.hedera.node.app.service.contract.impl;
    requires transitive com.hedera.node.app.service.file.impl;
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.network.admin.impl;
    requires transitive com.hedera.node.app.service.schedule.impl;
    requires transitive com.hedera.node.app.service.schedule;
    requires transitive com.hedera.node.app.service.token.impl;
    requires transitive com.hedera.node.app.service.token;
    requires transitive com.hedera.node.app.service.util.impl;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.merkledb;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.platform.core;
    requires transitive com.swirlds.virtualmap;
    requires transitive dagger;
    requires transitive grpc.stub;
    requires transitive javax.inject;
    requires com.hedera.node.app.hapi.fees;
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.contract;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.network.admin;
    requires com.hedera.node.app.service.util;
    requires com.google.common;
    requires com.google.protobuf;
    requires com.swirlds.base;
    requires com.swirlds.config.extensions;
    requires com.swirlds.fcqueue;
    requires com.swirlds.logging;
    requires grpc.netty;
    requires io.grpc;
    requires io.netty.handler;
    requires io.netty.transport.classes.epoll;
    requires io.netty.transport;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
    requires static com.google.auto.service;
    requires static java.compiler; // javax.annotation.processing.Generated

    exports com.hedera.node.app to
            com.hedera.node.test.clients;
    exports com.hedera.node.app.state to
            com.hedera.node.app.test.fixtures;
    exports com.hedera.node.app.workflows to
            com.hedera.node.app.test.fixtures;
    exports com.hedera.node.app.state.merkle to
            com.hedera.node.services.cli;
    exports com.hedera.node.app.state.merkle.disk to
            com.hedera.node.services.cli;
    exports com.hedera.node.app.state.merkle.memory to
            com.hedera.node.services.cli;
    exports com.hedera.node.app.workflows.dispatcher;
    exports com.hedera.node.app.config;
    exports com.hedera.node.app.workflows.handle.validation;
    exports com.hedera.node.app.signature to
            com.hedera.node.app.test.fixtures;
    exports com.hedera.node.app.info to
            com.hedera.node.app.test.fixtures;
    exports com.hedera.node.app.workflows.handle to
            com.hedera.node.app.test.fixtures;
    exports com.hedera.node.app.workflows.handle.record to
            com.hedera.node.app.test.fixtures;
    exports com.hedera.node.app.state.merkle.queue to
            com.swirlds.platform;
    exports com.hedera.node.app.version to
            com.hedera.node.app.test.fixtures,
            com.swirlds.platform;
    exports com.hedera.node.app.validation;
    exports com.hedera.node.app.state.listeners to
            com.hedera.node.app.test.fixtures;

    provides ConfigurationExtension with
            ServicesConfigExtension;
}
