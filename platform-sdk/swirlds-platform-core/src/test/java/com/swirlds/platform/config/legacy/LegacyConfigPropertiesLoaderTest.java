/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LegacyConfigPropertiesLoaderTest {

    @Test
    void testNullValue() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> LegacyConfigPropertiesLoader.loadConfigFile(null));
    }

    @Test
    void testInvalidPath() {
        final Path path = Paths.get("does", "not", "exits");
        Assertions.assertThrows(ConfigurationException.class, () -> LegacyConfigPropertiesLoader.loadConfigFile(path));
    }

    @Test
    void testEmptyConfig() {
        // given
        final Path path = Paths.get(
                LegacyConfigPropertiesLoaderTest.class.getResource("empty.txt").getPath());

        // when
        final LegacyConfigProperties properties = LegacyConfigPropertiesLoader.loadConfigFile(path);

        // then
        assertNotNull(properties, "The properties should never be null");
        Assertions.assertFalse(properties.appConfig().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.swirldName().isPresent(), "Value must not be set for an empty file");
    }

    @Test
    void testRealisticConfig() throws UnknownHostException {
        // given
        final Path path = Paths.get(
                LegacyConfigPropertiesLoaderTest.class.getResource("config.txt").getPath());

        // when
        final LegacyConfigProperties properties = LegacyConfigPropertiesLoader.loadConfigFile(path);

        // then
        assertNotNull(properties, "The properties should never be null");

        Assertions.assertTrue(properties.swirldName().isPresent(), "Value must be set");
        Assertions.assertEquals("123", properties.swirldName().get());

        Assertions.assertTrue(properties.appConfig().isPresent(), "Value must be set");
        Assertions.assertEquals(
                "HashgraphDemo.jar", properties.appConfig().get().jarName());
        Assertions.assertArrayEquals(
                new String[] {"1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "all"},
                properties.appConfig().get().params());

        final AddressBook addressBook = properties.getAddressBook();
        assertNotNull(addressBook);

        assertEquals(4, addressBook.getSize());

        final NodeId firstNode = addressBook.getNodeId(0);
        final Address firstAddress = addressBook.getAddress(firstNode);
        assertEquals(1L, firstAddress.getNodeId().id());

        final NodeId secondNode = addressBook.getNodeId(1);
        final Address secondAddress = addressBook.getAddress(secondNode);
        assertEquals(3L, secondAddress.getNodeId().id());

        final NodeId thirdNode = addressBook.getNodeId(2);
        final Address thirdAddress = addressBook.getAddress(thirdNode);
        assertEquals(20L, thirdAddress.getNodeId().id());

        final NodeId fourthNode = addressBook.getNodeId(3);
        final Address fourthAddress = addressBook.getAddress(fourthNode);
        assertEquals(95L, fourthAddress.getNodeId().id());
    }
}
