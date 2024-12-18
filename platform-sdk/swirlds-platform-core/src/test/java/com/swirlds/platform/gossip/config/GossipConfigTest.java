/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.sources.YamlConfigSource;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import org.junit.jupiter.api.Test;

public class GossipConfigTest {

    @Test
    void test() {
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new YamlConfigSource("gossip-config.yaml"))
                .getOrCreateConfig();

        assertNotNull(configuration);
    }
}
