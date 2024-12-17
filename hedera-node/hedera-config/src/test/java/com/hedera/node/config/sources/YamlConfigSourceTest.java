/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class YamlConfigSourceTest {

    @Test
    void testPropertyConfigSourceWhenPropertyFileNotFound() {
        // given
        final String nonExistentFile = "non-existent-file.yaml";

        // then
        assertThrows(UncheckedIOException.class, () -> new YamlConfigSource(nonExistentFile, 1));
    }

    @Test
    void testLoadProperties() throws Exception {
        // given
        final String existingFile = "test.yaml";

        // when
        final YamlConfigSource yamlConfigSource = new YamlConfigSource(existingFile, 1);

        // then
        assertNotNull(yamlConfigSource);
        assertTrue(yamlConfigSource.isListProperty("gossip.randomList"));
        assertTrue(yamlConfigSource.isListProperty("gossip.interfaceBindings"));
        assertEquals("42", yamlConfigSource.getValue("gossip.randomIntValue"));
        assertEquals(
                "{\"nodeId\":5,\"hostname\":\"10.10.10.45\",\"port\":23424}",
                yamlConfigSource.getValue("gossip.randomObj"));
    }
}
