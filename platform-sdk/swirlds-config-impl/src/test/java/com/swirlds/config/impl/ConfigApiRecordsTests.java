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

package com.swirlds.config.impl;

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.validation.ConfigViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigApiRecordsTests {

    @Test
    public void getConfigProxy() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", 8080))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        Assertions.assertEquals(8080, networkConfig.port(), "Config data objects should be configured correctly");
    }

    @Test
    public void getNotRegisteredDataObject() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataType(NetworkConfig.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "It should not be possible to create a config data object with undefined values");
    }

    @Test
    public void getConfigProxyUndefinedValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getConfigData(NetworkConfig.class),
                "It should not be possible to create an object of a not registered config data type");
    }

    @Test
    public void getConfigProxyDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", "8080"))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        Assertions.assertEquals(
                "localhost", networkConfig.server(), "Default values of config data objects should be used");
    }

    @Test
    public void getConfigProxyDefaultValuesList() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", "8080"))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);
        final List<Integer> errorCodes = networkConfig.errorCodes();
        final Set<Long> errorCodeSet = networkConfig.errorCodeSet();

        // then
        Assertions.assertNotNull(errorCodes, "Default values of config data objects should be used");
        Assertions.assertEquals(
                2, errorCodes.size(), "List values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(404), "List values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(500), "List values should be supported for default values in config data objects");
        Assertions.assertEquals(
                2, errorCodeSet.size(), "Set values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodeSet.contains(404L),
                "Set values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodeSet.contains(500L),
                "Set values should be supported for default values in config data objects");
    }

    @Test
    public void getConfigProxyValuesList() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", "8080"))
                .withSource(new SimpleConfigSource("network.errorCodes", "1,2,3"))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);
        final List<Integer> errorCodes = networkConfig.errorCodes();
        final Set<Long> errorCodeSet = networkConfig.errorCodeSet();

        // then
        Assertions.assertNotNull(errorCodes, "List values should be supported in config data objects");
        Assertions.assertEquals(3, errorCodes.size(), "List values should be supported in config data objects");
        Assertions.assertTrue(errorCodes.contains(1), "List values should be supported in config data objects");
        Assertions.assertTrue(errorCodes.contains(2), "List values should be supported in config data objects");
        Assertions.assertTrue(errorCodes.contains(3), "List values should be supported in config data objects");
        Assertions.assertEquals(3, errorCodeSet.size(), "Set values should be supported in config data objects");
        Assertions.assertTrue(errorCodeSet.contains(1L), "Set values should be supported in config data objects");
        Assertions.assertTrue(errorCodeSet.contains(2L), "Set values should be supported in config data objects");
        Assertions.assertTrue(errorCodeSet.contains(3L), "Set values should be supported in config data objects");
    }

    @Test
    public void invalidDataRecordWillFailInit() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataType(NetworkConfig.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "values must be defined for all properties that are defined by registered config data types");
    }

    @Test
    public void getConfigProxyOverwrittenDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", "8080"))
                .withSource(new SimpleConfigSource("network.server", "example.net"))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        Assertions.assertEquals(
                "example.net",
                networkConfig.server(),
                "It must be possible to overwrite default values in object data types");
    }

    @Test
    public void testMinConstrainAnnotation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("network.port", "-1"))
                .withConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Check for @Min annotation in NetworkConfig should end in violation");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals("network.port", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("-1", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be >= 1", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testConstrainAnnotation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("network.port", "8080"))
                .withSources(new SimpleConfigSource("network.server", "invalid"))
                .withConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Check for @Constraint annotation in NetworkConfig should end in violation");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "network.server", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("invalid", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "server must not be invalid", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testMultipleConstrainAnnotationsFail() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("network.port", "-1"))
                .withSources(new SimpleConfigSource("network.server", "invalid"))
                .withConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Check for @Constraint annotation in NetworkConfig should end in violation");

        // then
        Assertions.assertEquals(2, exception.getViolations().size());
    }

    @Test
    public void testNullDefaultsInConfigDataRecord() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(NullConfig.class)
                .build();

        // when
        final String value = configuration.getConfigData(NullConfig.class).value();
        final List<Integer> list = configuration.getConfigData(NullConfig.class).list();
        final Set<Integer> set = configuration.getConfigData(NullConfig.class).set();

        // then
        Assertions.assertNull(value);
        Assertions.assertNull(list);
        Assertions.assertNull(set);
    }

    @Test
    public void testEmptyCollectionsInConfigDataRecord() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(EmptyCollectionConfig.class)
                .build();

        // when
        final List<Integer> list =
                configuration.getConfigData(EmptyCollectionConfig.class).list();
        final Set<Integer> set =
                configuration.getConfigData(EmptyCollectionConfig.class).set();

        // then
        Assertions.assertIterableEquals(List.of(), list);
        Assertions.assertIterableEquals(Set.of(), set);
    }
}
