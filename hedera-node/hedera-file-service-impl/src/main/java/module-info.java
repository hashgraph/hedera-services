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

module com.hedera.node.app.service.file.impl {
    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.addressbook;
    requires transitive com.hedera.node.app.service.file;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.state.api;
    requires transitive dagger;
    requires transitive java.compiler; // javax.annotation.processing.Generated
    requires transitive javax.inject;
    requires com.swirlds.common;
    requires com.fasterxml.jackson.databind;
    requires com.google.protobuf;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.file.impl.handlers;
    exports com.hedera.node.app.service.file.impl.records;
    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.base;
    exports com.hedera.node.app.service.file.impl.utils;
    exports com.hedera.node.app.service.file.impl.schemas;
}
