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

package com.hedera.node.app.blocks.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.blocks.impl.streaming.BlockNodeConfigExtractor;
import com.hedera.node.internal.network.BlockNodeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockNodeConfigExtractorTest {
    private BlockNodeConfigExtractor blockNodeConfigExtractor;

    @BeforeEach
    void setUp() {
        // Ensure the test config file exists
        final var configPath = Objects.requireNonNull(
                        BlockNodeConfigExtractorTest.class.getClassLoader().getResource("bootstrap/"))
                .getPath();
        assertThat(Files.exists(Path.of(configPath))).isTrue();
        blockNodeConfigExtractor = new BlockNodeConfigExtractor(configPath);
    }

    @Test
    void testLoadConfig() {
        List<BlockNodeConfig> nodes = blockNodeConfigExtractor.getAllNodes();
        assertThat(nodes).isNotEmpty();
        assertThat(nodes).allMatch(node -> node.address() != null && node.port() > 0 && node.priority() > 0);
    }

    @Test
    void testMaxSimultaneousConnections() {
        int maxConnections = blockNodeConfigExtractor.getMaxSimultaneousConnections();
        assertThat(maxConnections).isEqualTo(1);
    }

    @Test
    void testNodeReselectionInterval() {
        Duration interval = blockNodeConfigExtractor.getNodeReselectionInterval();
        assertThat(interval).isNotNull();
        assertThat(interval.getSeconds()).isEqualTo(3600);
    }

    @Test
    void testBlockItemBatchSize() {
        int batchSize = blockNodeConfigExtractor.getBlockItemBatchSize();
        assertThat(batchSize).isEqualTo(256);
    }

    @Test
    void testInvalidConfigPath() {
        assertThatThrownBy(() -> new BlockNodeConfigExtractor("invalid/path"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read block node configuration");
    }
}
