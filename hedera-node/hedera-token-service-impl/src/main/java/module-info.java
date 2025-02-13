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

/**
 * Module that provides the implementation of the Hedera Token Service.
 */
module com.hedera.node.app.service.token.impl {
    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.token;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.state.api;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.google.common;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires static transitive com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.TokenServiceImpl;

    exports com.hedera.node.app.service.token.impl.handlers;
    exports com.hedera.node.app.service.token.impl;
    exports com.hedera.node.app.service.token.impl.api;
    exports com.hedera.node.app.service.token.impl.validators;
    exports com.hedera.node.app.service.token.impl.util;
    exports com.hedera.node.app.service.token.impl.handlers.staking;
    exports com.hedera.node.app.service.token.impl.handlers.transfer;
    exports com.hedera.node.app.service.token.impl.schemas;
    exports com.hedera.node.app.service.token.impl.comparator;
}
