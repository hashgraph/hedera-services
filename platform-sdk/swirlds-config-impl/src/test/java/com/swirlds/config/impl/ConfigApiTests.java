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

import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.config.sources.PropertyFileConfigSource;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.config.sources.SystemPropertiesConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.impl.converters.DurationConverter;
import com.swirlds.config.impl.validators.DefaultConfigViolation;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigApiTests {

    @Test
    public void checkNotExistingProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertFalse(configuration.exists("someName"), "A not defined config property should not exist");
    }

    @Test
    public void checkExistingProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("someName", "123"))
                .build();

        // then
        Assertions.assertTrue(configuration.exists("someName"), "A defined config property should exist");
    }

    @Test
    public void getAllPropertyNamesEmpty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final List<String> names = configuration.getPropertyNames().collect(Collectors.toList());

        // then
        Assertions.assertNotNull(names, "Even for an empty config the returned collection should not be null");
        Assertions.assertEquals(0, names.size(), "The config should not contain any properties");
    }

    @Test
    public void getAllPropertyNames() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("someName", "123"))
                .withSource(new SimpleConfigSource("someOtherName", "test"))
                .withSource(new SimpleConfigSource("company.url", "dummy"))
                .build();

        // when
        final List<String> names = configuration.getPropertyNames().collect(Collectors.toList());

        // then
        Assertions.assertNotNull(names, "The collection of config properties should never be null");
        Assertions.assertEquals(3, names.size(), "The config should contain 3 properties");
        Assertions.assertTrue(names.contains("someName"), "The config should contain the defined property");
        Assertions.assertTrue(names.contains("someOtherName"), "The config should contain the defined property");
        Assertions.assertTrue(names.contains("company.url"), "The config should contain the defined property");
    }

    @Test
    public void readNotExistingStringProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertThrows(
                NoSuchElementException.class,
                () -> configuration.getValue("someName"),
                "The config should throw an exception when trying to access a property that is not defined");
    }

    @Test
    public void readDefaultStringProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final String value = configuration.getValue("someName", "default");

        // then
        Assertions.assertEquals(
                "default", value, "The default value should be returned for a property that is not defined");
    }

    @Test
    public void readStringProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("someName", "123"))
                .build();

        // when
        final String value = configuration.getValue("someName");

        // then
        Assertions.assertEquals("123", value, "The defined value of the property should be returned");
    }

    @Test
    public void readStringPropertyWithDefaultProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("someName", "123"))
                .build();

        // when
        final String value = configuration.getValue("someName", "default");

        // then
        Assertions.assertEquals("123", value, "If a property is defined the default value should be ignored");
    }

    @Test
    public void readMissingStringPropertyWithNullProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final String value = configuration.getValue("someName", String.class, null);

        // then
        Assertions.assertNull(value, "Null should be an allowed default value");
    }

    @Test
    public void readIntProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("timeout", 12))
                .build();

        // when
        final int value = configuration.getValue("timeout", Integer.class);

        // then
        Assertions.assertEquals(12, value, "Integer value should be supported");
    }

    @Test
    public void readNotExistingIntProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertThrows(
                NoSuchElementException.class,
                () -> configuration.getValue("timeout", Integer.class),
                "The config should throw an exception when trying to access a property that is not defined");
    }

    @Test
    public void readBadIntProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("timeout", "NO-INT"))
                .build();

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValue("timeout", Integer.class),
                "The config should throw an exception when trying to access a property that can not be converted to "
                        + "it's defined type");
    }

    @Test
    public void readIntPropertyWithDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("timeout", 12))
                .build();

        // when
        final int value = configuration.getValue("timeout", Integer.class, 1_000);

        // then
        Assertions.assertEquals(12, value, "Default value should be ignored for a defined property");
    }

    @Test
    public void readMissingIntPropertyWithDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final int value = configuration.getValue("timeout", Integer.class, 1_000);

        // then
        Assertions.assertEquals(1_000, value, "The default value should be used for a property that is not defined");
    }

    @Test
    public void readMissingIntPropertyWithNullDefaultValue() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        Integer value = configuration.getValue("timeout", Integer.class, null);

        // then
        Assertions.assertNull(value, "Null should be allowed as default value");
    }

    @Test
    public void readMissingIntPropertyWithNullDefaultValueAutoboxing() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertThrows(
                NullPointerException.class,
                () -> {
                    int timeout = configuration.getValue("timeout", Integer.class, null);
                },
                "Autoboxing of null as default value should throw an exception for int");
    }

    @Test
    public void readListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource().withIntegerValues("testNumbers", List.of(1, 2, 3)))
                .build();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class);

        // then
        Assertions.assertEquals(3, values.size(), "A property that is defined as list should be parsed correctly");
        Assertions.assertEquals(
                1, values.get(0), "A property that is defined as list should contain the defined values");
        Assertions.assertEquals(
                2, values.get(1), "A property that is defined as list should contain the defined values");
        Assertions.assertEquals(
                3, values.get(2), "A property that is defined as list should contain the defined values");
    }

    @Test
    public void readListPropertyWithOneEntry() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("testNumbers", 123))
                .build();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class);

        // then
        Assertions.assertEquals(1, values.size(), "A property that is defined as list should be parsed correctly");
        Assertions.assertEquals(
                123, values.get(0), "A property that is defined as list should contain the defined values");
    }

    @Test
    public void readBadListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("testNumbers", "1,2,   3,4"))
                .build();

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValues("testNumbers", Integer.class),
                "given list property should not be parsed correctly");
    }

    @Test
    public void readDefaultListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class, List.of(6, 7, 8));

        // then
        Assertions.assertEquals(
                3, values.size(), "The default value should be used since no value is defined by the config");
        Assertions.assertEquals(6, values.get(0), "Should be part of the list since it is part of the default");
        Assertions.assertEquals(7, values.get(1), "Should be part of the list since it is part of the default");
        Assertions.assertEquals(8, values.get(2), "Should be part of the list since it is part of the default");
    }

    @Test
    public void readNullDefaultListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class, null);

        // then
        Assertions.assertNull(values, "Null should be a valid default value");
    }

    @Test
    public void checkListPropertyImmutable() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("testNumbers", "1,2,3"))
                .build();

        // when
        final List<Integer> values = configuration.getValues("testNumbers", Integer.class);

        // then
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> values.add(10),
                "List properties should always be immutable");
    }

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

        // then
        Assertions.assertNotNull(errorCodes, "Default values of config data objects should be used");
        Assertions.assertEquals(
                2, errorCodes.size(), "List values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(404), "List values should be supported for default values in config data objects");
        Assertions.assertTrue(
                errorCodes.contains(500), "List values should be supported for default values in config data objects");
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

        // then
        Assertions.assertNotNull(errorCodes, "List values should be supported in config data objects");
        Assertions.assertEquals(3, errorCodes.size(), "List values should be supported in config data objects");
        Assertions.assertTrue(errorCodes.contains(1), "List values should be supported in config data objects");
        Assertions.assertTrue(errorCodes.contains(2), "List values should be supported in config data objects");
        Assertions.assertTrue(errorCodes.contains(3), "List values should be supported in config data objects");
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
    public void getSystemProperty() {
        // given
        System.setProperty("test.config.sample", "qwerty");
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(SystemPropertiesConfigSource.getInstance())
                .build();

        // when
        String value = configuration.getValue("test.config.sample");

        // then
        Assertions.assertEquals("qwerty", value, "It must be possible to use system variables for the config");
    }

    @Test
    public void getPropertyFromFile() throws IOException, URISyntaxException {
        // given
        final Path configFile =
                Paths.get(ConfigApiTests.class.getResource("test.properties").toURI());
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new PropertyFileConfigSource(configFile))
                .build();

        // when
        String value = configuration.getValue("app.name");

        // then
        Assertions.assertEquals(
                "ConfigTest", value, "It must be possible to read variables for the config from a property file");
    }

    @Test
    public void getPropertyFromFileOverwritesDefault() throws IOException, URISyntaxException {
        // given
        final Path configFile =
                Paths.get(ConfigApiTests.class.getResource("test.properties").toURI());

        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new PropertyFileConfigSource(configFile))
                .withSources(new SimpleConfigSource("app.name", "unknown"))
                .build();

        // when
        String value = configuration.getValue("app.name");

        // then
        Assertions.assertEquals(
                "ConfigTest", value, "the used value must be based on the ordinal of the underlying config sources");
    }

    @Test
    public void checkValidationForNotExistingProperty() {
        // given
        final ConfigValidator validator = configuration -> {
            if (!configuration.exists("app.name")) {
                return Stream.of(new DefaultConfigViolation(
                        "app.name", null, false, "Property 'app.name' must not be " + "null"));
            }
            return Stream.empty();
        };
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withValidator(validator);

        // when
        final IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "Config must not be initialzed if a validation fails");

        // then
        Assertions.assertEquals(
                ConfigViolationException.class,
                exception.getClass(),
                "The specific exception type is used for a failed validation");
        final ConfigViolationException configViolationException = (ConfigViolationException) exception;
        Assertions.assertEquals(
                1, configViolationException.getViolations().size(), "The given init should only end in 1 violation");
        Assertions.assertEquals(
                "app.name",
                configViolationException.getViolations().get(0).getPropertyName(),
                "The violation should contain the correct property name");
        Assertions.assertEquals(
                "Property 'app.name' must not be null",
                configViolationException.getViolations().get(0).getMessage(),
                "The violation must contain the correct error message");
    }

    @Test
    public void loadLegacySettings() throws IOException, URISyntaxException {
        // given
        final Path configFile = Paths.get(
                ConfigApiTests.class.getResource("legacy-settings.txt").toURI());
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new LegacyFileConfigSource(configFile))
                .build();
        // then
        Assertions.assertEquals(
                8,
                configuration.getPropertyNames().count(),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("maxOutgoingSyncs"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("state.saveStatePeriod"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("showInternalStats"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("doUpnp"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("useLoopbackIp"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("csvFileName"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("checkSignedStateFromDisk"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertTrue(
                configuration.exists("loadKeysFromPfxFiles"),
                "It must be possible to read config properties from the old file format");

        Assertions.assertEquals(
                1,
                configuration.getValue("maxOutgoingSyncs", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                0,
                configuration.getValue("state.saveStatePeriod", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                1,
                configuration.getValue("showInternalStats", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertFalse(
                configuration.getValue("doUpnp", Boolean.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertFalse(
                configuration.getValue("useLoopbackIp", Boolean.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                "PlatformTesting",
                configuration.getValue("csvFileName"),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                1,
                configuration.getValue("checkSignedStateFromDisk", Integer.class),
                "It must be possible to read config properties from the old file format");
        Assertions.assertEquals(
                0,
                configuration.getValue("loadKeysFromPfxFiles", Integer.class),
                "It must be possible to read config properties from the old file format");
    }

    @Test
    public void registerConverterForTypeMultipleTimes() {
        // given
        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().withConverter(new DurationConverter());
        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "One 1 converter for a specific type / class can be registered");
    }

    @Test
    public void registerCustomConverter() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new TestDateConverter())
                .withSource(new SimpleConfigSource("app.start", "1662367513551"))
                .build();

        // when
        final Date date = configuration.getValue("app.start", Date.class);

        // then
        Assertions.assertEquals(
                new Date(1662367513551L), date, "The date should be converted correctly based on the given value");
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
    public void testNullList() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("sample.list", (String) null))
                .build();

        // when
        List<String> list1 = configuration.getValues("sample.list");
        List<String> list2 = configuration.getValues("sample.list", List.of());
        List<String> list3 = configuration.getValues("sample.list", String.class);
        List<String> list4 = configuration.getValues("sample.list", String.class, List.of());
        List<Integer> list5 = configuration.getValues("sample.list", Integer.class);
        List<Integer> list6 = configuration.getValues("sample.list", Integer.class, List.of());

        // then
        Assertions.assertNull(list1, "The list must be null");
        Assertions.assertNull(list2, "The list must be null since a default is only used if a property is not defined");
        Assertions.assertNull(list3, "The list must be null");
        Assertions.assertNull(list4, "The list must be null since a default is only used if a property is not defined");
        Assertions.assertNull(list5, "The list must be null");
        Assertions.assertNull(list6, "The list must be null since a default is only used if a property is not defined");
    }

    @Test
    public void testNullProperties() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("sample", (String) null))
                .build();

        // when
        String value1 = configuration.getValue("sample");
        String value2 = configuration.getValue("sample", "default");
        String value3 = configuration.getValue("sample", String.class);
        String value4 = configuration.getValue("sample", String.class, "default");

        Integer value5 = configuration.getValue("sample", Integer.class);
        Integer value6 = configuration.getValue("sample", Integer.class, 123);

        // then
        Assertions.assertNull(value1, "The value must be null");
        Assertions.assertNull(
                value2, "The value must be null since a default is only used if a property is not defined");
        Assertions.assertNull(value3, "The value must be null");
        Assertions.assertNull(
                value4, "The value must be null since a default is only used if a property is not defined");
        Assertions.assertNull(value5, "The value must be null");
        Assertions.assertNull(
                value6, "The value must be null since a default is only used if a property is not defined");
    }

    @Test
    public void testNotDefinedEmptyList() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertThrows(NoSuchElementException.class, () -> configuration.getValues("sample.list"));
        Assertions.assertThrows(
                NoSuchElementException.class, () -> configuration.getValues("sample.list", String.class));
        Assertions.assertThrows(
                NoSuchElementException.class, () -> configuration.getValues("sample.list", Integer.class));
    }

    @Test
    public void testEmptyListInConfigDataRecord() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(EmptyListConfig.class)
                .build();

        // when
        List<Integer> list = configuration.getConfigData(EmptyListConfig.class).list();

        // then
        Assertions.assertIterableEquals(List.of(), list);
    }

    @Test
    public void testNullDefaultsInConfigDataRecord() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(NullConfig.class)
                .build();

        // when
        List<Integer> list = configuration.getConfigData(NullConfig.class).list();
        String value = configuration.getConfigData(NullConfig.class).value();

        // then
        Assertions.assertNull(list);
        Assertions.assertNull(value);
    }
}
