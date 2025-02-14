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

module com.hedera.node.app.hapi.fees {
    exports com.hedera.node.app.hapi.fees.pricing;
    exports com.hedera.node.app.hapi.fees.usage.crypto;
    exports com.hedera.node.app.hapi.fees.usage.crypto.entities;
    exports com.hedera.node.app.hapi.fees.usage.consensus;
    exports com.hedera.node.app.hapi.fees.usage.state;
    exports com.hedera.node.app.hapi.fees.usage.schedule;
    exports com.hedera.node.app.hapi.fees.usage.token;
    exports com.hedera.node.app.hapi.fees.usage.token.meta;
    exports com.hedera.node.app.hapi.fees.usage.file;
    exports com.hedera.node.app.hapi.fees.usage;
    exports com.hedera.node.app.hapi.fees.usage.util;
    exports com.hedera.node.app.hapi.fees.calc;
    exports com.hedera.node.app.hapi.fees.usage.contract;
    exports com.hedera.node.app.hapi.fees.usage.token.entities;

    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.hapi;
    requires transitive com.google.common;
    requires transitive com.google.protobuf;
    requires transitive dagger;
    requires transitive static java.compiler; // javax.annotation.processing.Generated
    requires transitive javax.inject;
    requires com.fasterxml.jackson.databind;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static transitive com.github.spotbugs.annotations;
}
