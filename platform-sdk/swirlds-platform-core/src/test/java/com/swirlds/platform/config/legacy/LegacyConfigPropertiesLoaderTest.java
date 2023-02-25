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

import com.swirlds.common.internal.ConfigurationException;
import java.net.InetAddress;
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
        Assertions.assertNotNull(properties, "The properties should never be null");
        Assertions.assertFalse(properties.appConfig().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.tls().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.ipTos().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.swirldName().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.maxSyncs().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.genesisFreezeTime().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.saveStatePeriod().isPresent(), "Value must not be set for an empty file");
        Assertions.assertFalse(properties.transactionMaxBytes().isPresent(), "Value must not be set for an empty file");
        Assertions.assertEquals(
                0, properties.getAddressConfigs().size(), "no address config should be added for an empty file");
    }

    @Test
    void testRealisticConfig() throws UnknownHostException {
        // given
        final Path path = Paths.get(
                LegacyConfigPropertiesLoaderTest.class.getResource("config.txt").getPath());

        // when
        final LegacyConfigProperties properties = LegacyConfigPropertiesLoader.loadConfigFile(path);

        // then
        Assertions.assertNotNull(properties, "The properties should never be null");

        Assertions.assertFalse(
                properties.tls().isPresent(), "Value must not be set since it is not defined in the file");
        Assertions.assertFalse(
                properties.ipTos().isPresent(), "Value must not be set since it is not defined in the file");
        Assertions.assertFalse(
                properties.maxSyncs().isPresent(), "Value must not be set since it is not defined in the file");
        Assertions.assertFalse(
                properties.genesisFreezeTime().isPresent(),
                "Value must not be set since it is not defined in the file");
        Assertions.assertFalse(
                properties.saveStatePeriod().isPresent(), "Value must not be set since it is not defined in the file");
        Assertions.assertFalse(
                properties.transactionMaxBytes().isPresent(),
                "Value must not be set since it is not defined in the file");

        Assertions.assertTrue(properties.swirldName().isPresent(), "Value must be set");
        Assertions.assertEquals("123", properties.swirldName().get());

        Assertions.assertTrue(properties.appConfig().isPresent(), "Value must be set");
        Assertions.assertEquals(
                "HashgraphDemo.jar", properties.appConfig().get().jarName());
        Assertions.assertArrayEquals(
                new String[] {"1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "all"},
                properties.appConfig().get().params());

        Assertions.assertEquals(
                4, properties.getAddressConfigs().size(), "no address config should be added for an empty file");

        final InetAddress localhost = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});

        Assertions.assertEquals(
                "A", properties.getAddressConfigs().get(0).nickname(), "value must match to config" + ".txt");
        Assertions.assertEquals(
                "Alice", properties.getAddressConfigs().get(0).selfName(), "value must match to config.txt");
        Assertions.assertEquals(1, properties.getAddressConfigs().get(0).stake(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(0).internalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15301, properties.getAddressConfigs().get(0).internalPort(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(0).externalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15301, properties.getAddressConfigs().get(0).externalPort(), "value must match to config.txt");
        Assertions.assertEquals("", properties.getAddressConfigs().get(0).memo(), "value must match to config.txt");

        Assertions.assertEquals(
                "B", properties.getAddressConfigs().get(1).nickname(), "value must match to config" + ".txt");
        Assertions.assertEquals(
                "Bob", properties.getAddressConfigs().get(1).selfName(), "value must match to config.txt");
        Assertions.assertEquals(1, properties.getAddressConfigs().get(1).stake(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(1).internalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15302, properties.getAddressConfigs().get(1).internalPort(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(1).externalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15302, properties.getAddressConfigs().get(1).externalPort(), "value must match to config.txt");
        Assertions.assertEquals("", properties.getAddressConfigs().get(1).memo(), "value must match to config.txt");

        Assertions.assertEquals(
                "C", properties.getAddressConfigs().get(2).nickname(), "value must match to config" + ".txt");
        Assertions.assertEquals(
                "Carol", properties.getAddressConfigs().get(2).selfName(), "value must match to config.txt");
        Assertions.assertEquals(1, properties.getAddressConfigs().get(2).stake(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(2).internalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15303, properties.getAddressConfigs().get(2).internalPort(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(2).externalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15303, properties.getAddressConfigs().get(2).externalPort(), "value must match to config.txt");
        Assertions.assertEquals("", properties.getAddressConfigs().get(2).memo(), "value must match to config.txt");

        Assertions.assertEquals(
                "D", properties.getAddressConfigs().get(3).nickname(), "value must match to config" + ".txt");
        Assertions.assertEquals(
                "Dave", properties.getAddressConfigs().get(3).selfName(), "value must match to config.txt");
        Assertions.assertEquals(1, properties.getAddressConfigs().get(3).stake(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(3).internalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15304, properties.getAddressConfigs().get(3).internalPort(), "value must match to config.txt");
        Assertions.assertEquals(
                localhost,
                properties.getAddressConfigs().get(3).externalInetAddressName(),
                "value must match to config.txt");
        Assertions.assertEquals(
                15304, properties.getAddressConfigs().get(3).externalPort(), "value must match to config.txt");
        Assertions.assertEquals("", properties.getAddressConfigs().get(3).memo(), "value must match to config.txt");
    }
}
