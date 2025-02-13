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

module com.hedera.node.app.spi.test.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.workflows;
    exports com.hedera.node.app.spi.fixtures.util;
    exports com.hedera.node.app.spi.fixtures.info;

    requires transitive com.hedera.node.app.service.token; // TMP until FakePreHandleContext can be removed
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.state.api.test.fixtures;
    requires transitive com.swirlds.state.api;
    requires transitive com.swirlds.state.impl.test.fixtures;
    requires transitive org.apache.logging.log4j;
    requires transitive org.assertj.core;
    requires transitive org.junit.jupiter.api;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.common;
    requires com.swirlds.platform.core;
    requires com.google.common;
    requires org.apache.logging.log4j.core;
    requires static com.github.spotbugs.annotations;
}
