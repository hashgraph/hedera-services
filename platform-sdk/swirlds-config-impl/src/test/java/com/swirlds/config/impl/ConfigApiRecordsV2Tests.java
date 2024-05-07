/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.DefaultValue;
import com.swirlds.config.api.EmptyValue;
import com.swirlds.config.api.UnsetValue;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.api.validation.annotation.ConstraintMethod;
import com.swirlds.config.api.validation.annotation.Min;
import com.swirlds.config.extensions.sources.SimpleConfigSource;
import com.swirlds.config.impl.validators.DefaultConfigViolation;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigApiRecordsV2Tests {

    @Test
    void getConfigProxy() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", 8080))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        assertEquals(8080, networkConfig.port(), "Config data objects should be configured correctly");
    }

    @Test
    void getNotRegisteredDataObject() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataType(NetworkConfig.class);

        // then
        assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "It should not be possible to create a config data object with undefined values");
    }

    @Test
    void getConfigProxyUndefinedValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getConfigData(NetworkConfig.class),
                "It should not be possible to create an object of a not registered config data type");
    }

    @Test
    void getConfigProxyDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", "8080"))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        assertEquals("localhost", networkConfig.server(), "Default values of config data objects should be used");
    }

    @Test
    void getConfigProxyDefaultValuesList() {
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
        assertNotNull(errorCodes, "Default values of config data objects should be used");
        assertEquals(2, errorCodes.size(), "List values should be supported for default values in config data objects");
        assertTrue(
                errorCodes.contains(404), "List values should be supported for default values in config data objects");
        assertTrue(
                errorCodes.contains(500), "List values should be supported for default values in config data objects");
        assertEquals(
                2, errorCodeSet.size(), "Set values should be supported for default values in config data objects");
        assertTrue(
                errorCodeSet.contains(404L),
                "Set values should be supported for default values in config data objects");
        assertTrue(
                errorCodeSet.contains(500L),
                "Set values should be supported for default values in config data objects");
    }

    @Test
    void getConfigProxyValuesList() {
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
        assertNotNull(errorCodes, "List values should be supported in config data objects");
        assertEquals(3, errorCodes.size(), "List values should be supported in config data objects");
        assertTrue(errorCodes.contains(1), "List values should be supported in config data objects");
        assertTrue(errorCodes.contains(2), "List values should be supported in config data objects");
        assertTrue(errorCodes.contains(3), "List values should be supported in config data objects");
        assertEquals(3, errorCodeSet.size(), "Set values should be supported in config data objects");
        assertTrue(errorCodeSet.contains(1L), "Set values should be supported in config data objects");
        assertTrue(errorCodeSet.contains(2L), "Set values should be supported in config data objects");
        assertTrue(errorCodeSet.contains(3L), "Set values should be supported in config data objects");
    }

    @Test
    void invalidDataRecordWillFailInit() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConfigDataType(NetworkConfig.class);

        // then
        assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "values must be defined for all properties that are defined by registered config data types");
    }

    @Test
    void getConfigProxyOverwrittenDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("network.port", "8080"))
                .withSource(new SimpleConfigSource("network.server", "example.net"))
                .withConfigDataType(NetworkConfig.class)
                .build();

        // when
        final NetworkConfig networkConfig = configuration.getConfigData(NetworkConfig.class);

        // then
        assertEquals(
                "example.net",
                networkConfig.server(),
                "It must be possible to overwrite default values in object data types");
    }

    @Test
    void testMinConstrainAnnotation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("network.port", "-1"))
                .withConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception = assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Check for @Min annotation in NetworkConfig should end in violation");

        // then
        assertEquals(1, exception.getViolations().size());
        assertTrue(exception.getViolations().get(0).propertyExists());
        assertEquals("network.port", exception.getViolations().get(0).getPropertyName());
        assertEquals("-1", exception.getViolations().get(0).getPropertyValue());
        assertEquals("Value must be >= 1", exception.getViolations().get(0).getMessage());
    }

    @Test
    void testConstrainAnnotation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("network.port", "8080"))
                .withSources(new SimpleConfigSource("network.server", "invalid"))
                .withConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception = assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Check for @Constraint annotation in NetworkConfig should end in violation");

        // then
        assertEquals(1, exception.getViolations().size());
        assertTrue(exception.getViolations().get(0).propertyExists());
        assertEquals("network.server", exception.getViolations().get(0).getPropertyName());
        assertEquals("invalid", exception.getViolations().get(0).getPropertyValue());
        assertEquals(
                "server must not be invalid", exception.getViolations().get(0).getMessage());
    }

    @Test
    void testMultipleConstrainAnnotationsFail() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("network.port", "-1"))
                .withSources(new SimpleConfigSource("network.server", "invalid"))
                .withConfigDataType(NetworkConfig.class);

        // when
        final ConfigViolationException exception = assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Check for @Constraint annotation in NetworkConfig should end in violation");

        // then
        assertEquals(2, exception.getViolations().size());
    }

    @Test
    void testNullDefaultsInConfigDataRecord() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(UnSetConfig.class)
                .build();

        // when
        final String value = configuration.getConfigData(UnSetConfig.class).value();
        final List<Integer> list =
                configuration.getConfigData(UnSetConfig.class).list();
        final Set<Integer> set = configuration.getConfigData(UnSetConfig.class).set();
        final int number = configuration.getConfigData(UnSetConfig.class).number();
        final char c = configuration.getConfigData(UnSetConfig.class).character();

        // then
        assertNull(value);
        assertNull(list);
        assertNull(set);
        assertEquals(0, number);
        assertEquals('\u0000', c);
    }

    @Test
    void testEmptyCollectionsInConfigDataRecord() {
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
        assertIterableEquals(List.of(), list);
        assertIterableEquals(Set.of(), set);
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                InvalidRecordConfig.class,
                InvalidRecordConfig2.class,
                InvalidRecordConfig3.class,
                InvalidRecordConfig4.class
            })
    void testInvalidDataRecord(Class<? extends Record> clazz) {
        assertThrows(
                IllegalStateException.class,
                () -> ConfigurationBuilder.create().withConfigDataType(clazz).build(),
                "Config should fail at startup with invalid record");
    }

    @Test
    void testInvalidAnnotationForTypeInDataRecord() {
        assertThrows(
                IllegalStateException.class,
                () -> ConfigurationBuilder.create()
                        .withConfigDataType(InvalidAnnotationForTypeRecord.class)
                        .build(),
                "Config should fail at startup with invalid record");
    }

    @ConfigData("null")
    public record UnSetConfig(
            @UnsetValue List<Integer> list,
            @UnsetValue Set<Integer> set,
            @UnsetValue String value,
            @UnsetValue int number,
            @UnsetValue char character) {}

    @ConfigData("network")
    public record NetworkConfig(
            @Min(1) int port,
            @ConstraintMethod("checkServer") @DefaultValue("localhost") String server,
            @ConfigProperty("errorCodes") @DefaultValue("404,500") List<Integer> errorCodes,
            @ConfigProperty("errorCodes") @DefaultValue("404,500") Set<Long> errorCodeSet) {

        public ConfigViolation checkServer(final Configuration configuration) {
            if (Objects.equals("invalid", server)) {
                return new DefaultConfigViolation("network.server", server, true, "server must not be invalid");
            }
            return null;
        }
    }

    @ConfigData("empty")
    public record EmptyCollectionConfig(
            @EmptyValue String string, @EmptyValue List<Integer> list, @EmptyValue Set<Integer> set) {}

    @ConfigData
    public record InvalidRecordConfig(
            @ConfigProperty(value = "errorCodes", defaultValue = "value") @DefaultValue("value2") String string) {}

    @ConfigData
    public record InvalidRecordConfig2(@DefaultValue("value1") @EmptyValue String string) {}

    @ConfigData
    public record InvalidRecordConfig3(@DefaultValue("value1") @UnsetValue String string) {}

    @ConfigData
    public record InvalidRecordConfig4(@EmptyValue @UnsetValue String string) {}

    @ConfigData
    public record InvalidAnnotationForTypeRecord(@EmptyValue int value) {}
}
