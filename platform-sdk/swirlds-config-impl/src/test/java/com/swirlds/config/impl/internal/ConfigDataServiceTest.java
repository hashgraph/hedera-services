/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.internal;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.extensions.sources.SimpleConfigSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigDataServiceTest {

    private ConverterService converterService;

    @BeforeEach
    public void setUp() {
        converterService = new ConverterService();
        converterService.init();
    }

    @Test
    void itSuccessfullyCreatesAndReturnsAConverter() {
        // given
        final ConverterService cs = converterService;

        // then:
        Assertions.assertNotNull(cs.getConverterForType(NumberEnum.class));
    }

    @Test
    void itSuccessfullyConvertsValueToRequestedEnum() {
        // given
        final ConverterService cs = converterService;

        // then:
        Assertions.assertEquals(NumberEnum.ONE, cs.convert("ONE", NumberEnum.class));
    }

    @Test
    void itSuccessfullyConvertsValueToRequestedEnum2() {
        // given
        final ConverterService cs = converterService;

        // then:
        Assertions.assertEquals(NumberAndValueEnum.ONE, cs.convert("ONE", NumberAndValueEnum.class));
    }

    @Test
    void itSuccessfullyConvertsValueToRequestedEnum3() {
        // given
        final ConverterService cs = converterService;

        // then:
        Assertions.assertEquals(SpecialCharacterEnum.Ñ, cs.convert("Ñ", SpecialCharacterEnum.class));
    }

    @Test
    void itSuccessfullyConvertsEnumWithSameConstantName() {
        // given
        final ConverterService cs = converterService;
        // pre-call this enum has the same constants as the next call, should not interfere
        cs.getConverterForType(NumberEnum.class);

        // then:
        Assertions.assertEquals(NumberAndValueEnum.ONE, cs.convert("ONE", NumberAndValueEnum.class));
    }

    @Test
    void itDoesNotCreateANewInstanceForAlreadyCachedConverters() {
        // given
        final ConverterService cs = converterService;
        ConfigConverter<NumberEnum> converter = cs.getConverterForType(NumberEnum.class);

        // then:
        Assertions.assertSame(converter, cs.getConverterForType(NumberEnum.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"One", "one", "onE", "oNe", " ONE", "ONE ", "DOS", "", "OnE", "null"})
    void itFailsConvertingInvalidEnumValues(final String param) {
        // given
        final ConverterService cs = converterService;
        // then
        Assertions.assertThrows(IllegalArgumentException.class, () -> cs.convert(param, NumberEnum.class));
    }

    @Test
    void itReturnsNullForNullValues() {
        // given
        final ConverterService cs = converterService;
        // then:
        Assertions.assertNull(cs.convert(null, NumberAndValueEnum.class));
    }

    @Test
    void itSuccessfullyUsesConfiguredEnumConverterAboveDefaultConverter() {
        // given
        ConverterService cs = new ConverterService();
        FakeEnumConverter converter = new FakeEnumConverter();
        cs.addConverter(converter); // creates a new enumConverter for NumberAndValueEnum
        cs.init();

        // then:
        // NumberValueEnum  gets converted with FakeEnumConverter
        Assertions.assertSame(converter, cs.getConverterForType(NumberAndValueEnum.class));
        Assertions.assertEquals(NumberAndValueEnum.ONE, cs.convert("ONE", NumberAndValueEnum.class));
        Assertions.assertEquals(NumberAndValueEnum.ONE, cs.convert("", NumberAndValueEnum.class));
        Assertions.assertEquals(NumberAndValueEnum.ONE, cs.convert("DOS", NumberAndValueEnum.class));
        // and:
        // NumberEnum stills gets converted with defaultEnumConverter
        Assertions.assertEquals(NumberEnum.ONE, cs.convert("ONE", NumberEnum.class));
        Assertions.assertThrows(IllegalArgumentException.class, () -> cs.convert("", NumberEnum.class));
        Assertions.assertThrows(IllegalArgumentException.class, () -> cs.convert("DOS", NumberEnum.class));
    }

    @Test
    void testIntegration() {

        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("plain-value", "ONE"))
                .withSource(new SimpleConfigSource("complex-value-one", "ONE"))
                .withSource(new SimpleConfigSource("complex-value-two", "DOS"))
                .withSource(new SimpleConfigSource("complex-value-three", "THREE"))
                .build();

        // when
        final NumberEnum uno = configuration.getValue("plain-value", NumberEnum.class);
        final NumberAndValueEnum one = configuration.getValue("complex-value-one", NumberAndValueEnum.class);

        // then
        Assertions.assertEquals(uno, NumberEnum.ONE);
        Assertions.assertEquals(one, NumberAndValueEnum.ONE);
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValue("complex-value-two", NumberAndValueEnum.class));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValue("complex-value-three", NumberAndValueEnum.class));
    }

    /**
     * An enum converter that always returns the same constant
     */
    private static class FakeEnumConverter implements ConfigConverter<NumberAndValueEnum> {
        @Override
        public NumberAndValueEnum convert(String value) throws IllegalArgumentException, NullPointerException {
            return NumberAndValueEnum.ONE;
        }
    }

    private enum NumberEnum {
        ONE
    }

    private enum NumberAndValueEnum {
        ONE(1);
        final int value;

        NumberAndValueEnum(int value) {
            this.value = value;
        }
    }

    private enum SpecialCharacterEnum {
        Ñ,
    }
}
