/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

module com.hedera.node.test.clients {
    exports com.hedera.services.bdd.spec.dsl;
    exports com.hedera.services.bdd.spec.dsl.contracts;
    exports com.hedera.services.bdd.spec.dsl.utils;
    exports com.hedera.services.bdd.suites;
    exports com.hedera.services.bdd.suites.utils.sysfiles.serdes;
    exports com.hedera.services.bdd.suites.utils.contracts;
    exports com.hedera.services.bdd.suites.utils.contracts.precompile;
    exports com.hedera.services.bdd.spec;
    exports com.hedera.services.bdd.spec.infrastructure;
    exports com.hedera.services.bdd.spec.infrastructure.meta;
    exports com.hedera.services.bdd.spec.props;
    exports com.hedera.services.bdd.spec.queries;
    exports com.hedera.services.bdd.spec.queries.file;
    exports com.hedera.services.bdd.spec.queries.meta;
    exports com.hedera.services.bdd.spec.queries.token;
    exports com.hedera.services.bdd.spec.queries.crypto;
    exports com.hedera.services.bdd.spec.queries.schedule;
    exports com.hedera.services.bdd.spec.queries.consensus;
    exports com.hedera.services.bdd.spec.queries.contract;
    exports com.hedera.services.bdd.spec.transactions;
    exports com.hedera.services.bdd.spec.utilops;
    exports com.hedera.services.bdd.spec.utilops.checks;
    exports com.hedera.services.bdd.spec.utilops.embedded;
    exports com.hedera.services.bdd.spec.utilops.grouping;
    exports com.hedera.services.bdd.spec.utilops.inventory;
    exports com.hedera.services.bdd.spec.utilops.lifecycle;
    exports com.hedera.services.bdd.spec.utilops.lifecycle.ops;
    exports com.hedera.services.bdd.spec.utilops.mod;
    exports com.hedera.services.bdd.spec.utilops.pauses;
    exports com.hedera.services.bdd.spec.utilops.streams;
    exports com.hedera.services.bdd.spec.utilops.upgrade;
    exports com.hedera.services.bdd.spec.utilops.streams.assertions;
    exports com.hedera.services.bdd.suites.meta;
    exports com.hedera.services.bdd.spec.keys.deterministic;
    exports com.hedera.services.bdd.spec.transactions.file;
    exports com.hedera.services.bdd.spec.transactions.lambda;
    exports com.hedera.services.bdd.spec.transactions.token;
    exports com.hedera.services.bdd.spec.transactions.node;
    exports com.hedera.services.bdd.spec.keys;
    exports com.hedera.services.bdd.spec.transactions.crypto;
    exports com.hedera.services.bdd.spec.transactions.schedule;
    exports com.hedera.services.bdd.spec.transactions.consensus;
    exports com.hedera.services.bdd.spec.transactions.contract;
    exports com.hedera.services.bdd.spec.transactions.network;
    exports com.hedera.services.bdd.spec.transactions.util;
    exports com.hedera.services.bdd.spec.transactions.system;
    exports com.hedera.services.bdd.suites.perf;
    exports com.hedera.services.bdd.suites.staking;
    exports com.hedera.services.bdd.spec.fees;
    exports com.hedera.services.bdd.spec.verification.traceability;
    exports com.hedera.services.bdd.spec.assertions;
    exports com.hedera.services.bdd.spec.assertions.matchers;
    exports com.hedera.services.bdd.junit;
    exports com.hedera.services.bdd.junit.hedera;
    exports com.hedera.services.bdd.junit.hedera.embedded;
    exports com.hedera.services.bdd.junit.hedera.embedded.fakes;
    exports com.hedera.services.bdd.junit.hedera.subprocess;
    exports com.hedera.services.bdd.junit.extensions;
    exports com.hedera.services.bdd.junit.support.validators;
    exports com.hedera.services.bdd.junit.support;
    exports com.hedera.services.bdd.junit.support.validators.utils;
    exports com.hedera.services.bdd.junit.support.validators.block;
    exports com.hedera.services.bdd.utils;
    exports com.hedera.services.bdd.junit.restart;

    requires com.hedera.cryptography.tss;
    requires com.hedera.node.app.hapi.fees;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.addressbook.impl;
    requires com.hedera.node.app.service.addressbook;
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.schedule.impl;
    requires com.hedera.node.app.service.schedule;
    requires com.hedera.node.app.service.token.impl;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.spi;
    requires com.hedera.node.app.test.fixtures;
    requires com.hedera.node.app;
    requires com.hedera.node.config;
    requires com.hedera.node.hapi;
    requires com.swirlds.base.test.fixtures;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires com.swirlds.merkledb;
    requires com.swirlds.metrics.api;
    requires com.swirlds.platform.core.test.fixtures;
    requires com.swirlds.platform.core;
    requires com.swirlds.state.api;
    requires com.swirlds.state.impl;
    requires com.swirlds.virtualmap;
    requires com.esaulpaugh.headlong;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.github.dockerjava.api;
    requires com.google.common;
    requires com.google.protobuf;
    requires com.sun.jna;
    requires io.grpc.netty;
    requires io.grpc.stub;
    requires io.grpc;
    requires io.netty.handler;
    requires java.desktop;
    requires java.net.http;
    requires net.i2p.crypto.eddsa;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.assertj.core;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.internal.crypto;
    requires org.hyperledger.besu.nativelib.secp256k1;
    requires org.json;
    requires org.junit.jupiter.api;
    requires org.junit.platform.commons;
    requires org.junit.platform.launcher;
    requires org.opentest4j;
    requires org.testcontainers;
    requires org.yaml.snakeyaml;
    requires tuweni.bytes;
    requires tuweni.units;
    requires static com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
    requires static org.junit.platform.engine;
}
