import com.swirlds.config.api.ConfigurationExtension;
import com.swirlds.platform.config.PlatformConfigurationExtension;

/**
 * The Swirlds public API module used by platform applications.
 */
module com.swirlds.platform.core {

    /* Public Package Exports. This list should remain alphabetized. */
    exports com.swirlds.platform;
    exports com.swirlds.platform.builder;
    exports com.swirlds.platform.network.communication.handshake;
    exports com.swirlds.platform.cli;
    exports com.swirlds.platform.components;
    exports com.swirlds.platform.components.appcomm;
    exports com.swirlds.platform.components.common.output;
    exports com.swirlds.platform.components.state.output;
    exports com.swirlds.platform.config;
    exports com.swirlds.platform.config.legacy;
    exports com.swirlds.platform.event.report;
    exports com.swirlds.platform.gui.hashgraph;
    exports com.swirlds.platform.gui.hashgraph.internal;
    exports com.swirlds.platform.network.connection;
    exports com.swirlds.platform.network.connectivity;
    exports com.swirlds.platform.event.validation;
    exports com.swirlds.platform.eventhandling;
    exports com.swirlds.platform.gui;
    exports com.swirlds.platform.gui.model;
    exports com.swirlds.platform.health;
    exports com.swirlds.platform.health.clock;
    exports com.swirlds.platform.health.entropy;
    exports com.swirlds.platform.health.filesystem;
    exports com.swirlds.platform.listeners;
    exports com.swirlds.platform.metrics;
    exports com.swirlds.platform.network;
    exports com.swirlds.platform.network.communication;
    exports com.swirlds.platform.network.protocol;
    exports com.swirlds.platform.network.topology;
    exports com.swirlds.platform.recovery;
    exports com.swirlds.platform.sequence;
    exports com.swirlds.platform.sequence.map;
    exports com.swirlds.platform.sequence.set;
    exports com.swirlds.platform.state;
    exports com.swirlds.platform.stats;
    exports com.swirlds.platform.stats.atomic;
    exports com.swirlds.platform.stats.cycle;
    exports com.swirlds.platform.state.editor;
    exports com.swirlds.platform.stats.simple;
    exports com.swirlds.platform.state.signed;
    exports com.swirlds.platform.state.address;
    exports com.swirlds.platform.gossip.sync;
    exports com.swirlds.platform.scratchpad;
    exports com.swirlds.platform.system;
    exports com.swirlds.platform.system.address;
    exports com.swirlds.platform.system.events;
    exports com.swirlds.platform.system.transaction;
    exports com.swirlds.platform.system.state.notifications;
    exports com.swirlds.platform.system.status;
    exports com.swirlds.platform.system.status.actions;
    exports com.swirlds.platform.util;

    /* Targeted Exports to External Libraries */
    exports com.swirlds.platform.internal to
            com.swirlds.platform.test,
            com.swirlds.platform.core.test.fixtures,
            com.fasterxml.jackson.core,
            com.fasterxml.jackson.databind;
    exports com.swirlds.platform.swirldapp to
            com.swirlds.platform.test;
    exports com.swirlds.platform.consensus to
            com.swirlds.platform.test,
            com.swirlds.platform.core.test.fixtures,
            com.hedera.node.app;
    exports com.swirlds.platform.crypto to
            com.swirlds.platform.test,
            com.hedera.node.test.clients,
            com.swirlds.platform.core.test.fixtures,
            com.hedera.node.app.test.fixtures,
            com.hedera.node.app;
    exports com.swirlds.platform.event.linking to
            com.swirlds.common,
            com.swirlds.platform.test,
            com.swirlds.platform.core.test.fixtures;
    exports com.swirlds.platform.state.notifications to
            com.swirlds.platform.test;
    exports com.swirlds.platform.state.iss to
            com.swirlds.platform.test;
    exports com.swirlds.platform.state.iss.internal to
            com.swirlds.platform.test;
    exports com.swirlds.platform.recovery.internal to
            com.swirlds.platform.test;
    exports com.swirlds.platform.uptime to
            com.swirlds.config.impl,
            com.swirlds.common,
            com.hedera.node.test.clients;
    exports com.swirlds.platform.gossip.sync.config to
            com.swirlds.config.impl,
            com.swirlds.common,
            com.hedera.node.test.clients;

    opens com.swirlds.platform.cli to
            info.picocli;

    exports com.swirlds.platform.components.transaction;
    exports com.swirlds.platform.components.transaction.system;
    exports com.swirlds.platform.event.preconsensus;
    exports com.swirlds.platform.gossip.sync.protocol;
    exports com.swirlds.platform.gossip;
    exports com.swirlds.platform.reconnect;
    exports com.swirlds.platform.gossip.shadowgraph;
    exports com.swirlds.platform.recovery.emergencyfile;
    exports com.swirlds.platform.event;
    exports com.swirlds.platform.event.creation.tipset to
            com.hedera.node.test.clients,
            com.swirlds.common,
            com.swirlds.config.impl;
    exports com.swirlds.platform.wiring;
    exports com.swirlds.platform.wiring.components;
    exports com.swirlds.platform.event.hashing;
    exports com.swirlds.platform.event.orphan;
    exports com.swirlds.platform.publisher;
    exports com.swirlds.platform.components.consensus;
    exports com.swirlds.platform.pool;
    exports com.swirlds.platform.state.snapshot;
    exports com.swirlds.platform.state.service.schemas;
    exports com.swirlds.platform.state.service;
    exports com.swirlds.platform.builder.internal;
    exports com.swirlds.platform.config.internal;
    exports com.swirlds.platform.roster;

    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.base;
    requires transitive com.swirlds.cli;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.state.api;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.hedera.pbj.runtime;
    requires transitive info.picocli;
    requires transitive org.apache.logging.log4j;
    requires com.swirlds.config.extensions;
    requires com.swirlds.logging;
    requires com.swirlds.merkle;
    requires com.swirlds.merkledb;
    requires com.swirlds.virtualmap;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires java.desktop;
    requires java.management;
    requires java.scripting;
    requires jdk.management;
    requires jdk.net;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires static transitive com.github.spotbugs.annotations;
    requires static transitive com.google.auto.service;

    provides ConfigurationExtension with
            PlatformConfigurationExtension;
}
