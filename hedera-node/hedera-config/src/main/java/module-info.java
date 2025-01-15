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

module com.hedera.node.config {
    exports com.hedera.node.config;
    exports com.hedera.node.config.converter;
    exports com.hedera.node.config.data;
    exports com.hedera.node.config.sources;
    exports com.hedera.node.config.types;
    exports com.hedera.node.config.validation;

    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.config.extensions;
    requires com.swirlds.common;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires org.testcontainers;
    requires static com.github.spotbugs.annotations;
}
