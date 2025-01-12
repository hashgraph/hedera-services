/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.sources.YamlConfigSource;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

public class GossipConfigTest {

    @Test
    void testReadingFile() throws UnknownHostException {
        final Configuration configuration = new TestConfigBuilder()
                .withSource(new YamlConfigSource("node-overrides.yaml"))
                .getOrCreateConfig();
        assertNotNull(configuration);

        final GossipConfig gossipConfig = configuration.getConfigData(GossipConfig.class);
        assertNotNull(gossipConfig);

        assertNotNull(gossipConfig.interfaceBindings());
        assertEquals(4, gossipConfig.interfaceBindings().size());
        assertEquals(4, gossipConfig.endpointOverrides().size());

        final NetworkEndpoint endpoint = gossipConfig.interfaceBindings().getFirst();
        assertEquals(0, endpoint.nodeId());
        assertEquals(InetAddress.getByName("10.10.10.1"), endpoint.hostname());
        assertEquals(1234, endpoint.port());

        final NetworkEndpoint override = gossipConfig.endpointOverrides().getFirst();
        assertEquals(5, override.nodeId());
        assertEquals(InetAddress.getByName("10.10.10.11"), override.hostname());
        assertEquals(1238, override.port());
    }
}
