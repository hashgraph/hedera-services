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

package com.swirlds.config.extensions.test.sources;

import com.swirlds.config.extensions.sources.YamlConfigSource;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class YamlConfigSourceTest {

    @Test
    void testPropertyConfigSourceWhenPropertyFileNotFound() {
        // given
        final String nonExistentFile = "non-existent-file.yaml";

        // then
        Assertions.assertThrows(UncheckedIOException.class, () -> new YamlConfigSource(nonExistentFile, 1));
    }

    @Test
    void testLoadProperties() {
        // given
        final String existingFile = "test.yaml";

        // when
        final YamlConfigSource yamlConfigSource = new YamlConfigSource(existingFile, 1);

        // then
        Assertions.assertNotNull(yamlConfigSource);

        Assertions.assertTrue(yamlConfigSource.isListProperty("gossip.networkEndpoints"));
        final List<String> interfaceBindings = yamlConfigSource.getListValue("gossip.networkEndpoints");
        Assertions.assertEquals(4, interfaceBindings.size());
        Assertions.assertEquals("{\"nodeId\":0,\"hostname\":\"10.10.10.1\",\"port\":1234}", interfaceBindings.get(0));
        Assertions.assertEquals("{\"nodeId\":0,\"hostname\":\"10.10.10.2\",\"port\":1234}", interfaceBindings.get(1));
        Assertions.assertEquals("{\"nodeId\":1,\"hostname\":\"10.10.10.3\",\"port\":1234}", interfaceBindings.get(2));
        Assertions.assertEquals(
                "{\"nodeId\":4,\"hostname\":\"2001:db8:3333:4444:5555:6666:7777:8888\",\"port\":1234}",
                interfaceBindings.get(3));

        Assertions.assertEquals("random", yamlConfigSource.getValue("gossip.randomStringValue"));
        Assertions.assertEquals("42", yamlConfigSource.getValue("gossip.randomIntValue"));
        Assertions.assertEquals(
                "{\"nodeId\":5,\"hostname\":\"10.10.10.45\",\"port\":23424}",
                yamlConfigSource.getValue("gossip.randomObj"));

        Assertions.assertTrue(yamlConfigSource.isListProperty("gossip.randomList"));
        final List<String> randomList = yamlConfigSource.getListValue("gossip.randomList");
        Assertions.assertEquals(2, randomList.size());
        Assertions.assertEquals("foo", randomList.get(0));
        Assertions.assertEquals("bar", randomList.get(1));

        Assertions.assertTrue(yamlConfigSource.isListProperty("gossip.randomListOfList"));
    }
}
