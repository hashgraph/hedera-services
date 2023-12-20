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
import com.swirlds.config.impl.converters.EnumConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConverterServiceTest {

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

        ConfigConverter<NumberEnum> converterForType = cs.getConverterForType(NumberEnum.class);
        // then:
        Assertions.assertNotNull(converterForType);
        Assertions.assertInstanceOf(EnumConverter.class, converterForType);
    }

    @Test
    void itDoesNotCreateANewInstanceForAlreadyCachedConverters() {
        // given
        final ConverterService cs = converterService;
        ConfigConverter<NumberEnum> converter = cs.getConverterForType(NumberEnum.class);

        // then:
        Assertions.assertSame(converter, cs.getConverterForType(NumberEnum.class));
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
        Assertions.assertInstanceOf(FakeEnumConverter.class, cs.getConverterForType(NumberAndValueEnum.class));
        // and:
        // NumberEnum stills gets converted with defaultEnumConverter
        Assertions.assertEquals(NumberEnum.ONE, cs.convert("ONE", NumberEnum.class));
        Assertions.assertThrows(IllegalArgumentException.class, () -> cs.convert("", NumberEnum.class));
        Assertions.assertThrows(IllegalArgumentException.class, () -> cs.convert("DOS", NumberEnum.class));
        Assertions.assertInstanceOf(EnumConverter.class, cs.getConverterForType(NumberEnum.class));
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
        public NumberAndValueEnum convert(String value) {
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
}
