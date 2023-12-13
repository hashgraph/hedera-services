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

class ConfigDataServiceTest {

    private ConverterService converterService;

    @BeforeEach
    public void setUp() {
        converterService = new ConverterService();
        converterService.init();
    }

    @Test
    public void converterReturnsNotNull() {
        // given
        ConverterService cs = converterService;

        // then:
        Assertions.assertNotNull(cs.getConverterForType(TestPlainEnum.class));
    }

    @Test
    public void converterConvertsValueToPlainTestEnum() {
        // given
        ConverterService cs = converterService;

        // then:
        Assertions.assertEquals(TestPlainEnum.UNO, cs.convert("UNO", TestPlainEnum.class));
    }

    @Test
    public void converterConvertsValueToComplexTestEnum() {
        // given
        ConverterService cs = converterService;

        // then:
        Assertions.assertEquals(TestComplexEnum.ONE, cs.convert("ONE", TestComplexEnum.class));
    }

    @Test
    public void converterConvertsValueToComplexTestEnumEvenHavingASimilarEnum() {
        // given
        ConverterService cs = converterService;
        cs.getConverterForType(TestPlainEnum.class);

        // then:
        Assertions.assertEquals(TestComplexEnum.ONE, cs.convert("ONE", TestComplexEnum.class));
    }

    @Test
    public void itDoesNotCreateANewInstanceForAlreadyAskedConverters() {
        // given
        ConverterService cs = converterService;
        ConfigConverter<TestPlainEnum> converterForTypePlainTestEnum = cs.getConverterForType(TestPlainEnum.class);

        // then:
        Assertions.assertSame(converterForTypePlainTestEnum, cs.getConverterForType(TestPlainEnum.class));
    }

    @Test
    public void converterThrowsIllegalArgumentIfDoesNotMatch() {
        // given
        ConverterService cs = converterService;

        // then:
        Assertions.assertThrows(IllegalArgumentException.class, () -> cs.convert("DOS", TestPlainEnum.class));
    }

    @Test
    void testIntegration() {

        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("plain-value", "UNO"))
                .withSource(new SimpleConfigSource("complex-value-one", "ONE"))
                .withSource(new SimpleConfigSource("complex-value-two", "DOS"))
                .withSource(new SimpleConfigSource("complex-value-three", "THREE"))
                .withConverter(new TestComplexEnumConverter())
                .build();

        // when
        final TestPlainEnum uno = configuration.getValue("plain-value", TestPlainEnum.class);
        final TestComplexEnum one = configuration.getValue("complex-value-one", TestComplexEnum.class);
        final TestComplexEnum two = configuration.getValue("complex-value-two", TestComplexEnum.class);
        final TestComplexEnum three = configuration.getValue("complex-value-three", TestComplexEnum.class);

        // then
        Assertions.assertEquals(uno, TestPlainEnum.UNO);
        Assertions.assertEquals(one, TestComplexEnum.ONE);
        Assertions.assertEquals(two, TestComplexEnum.ONE);
        Assertions.assertEquals(three, TestComplexEnum.ONE);
    }

    private static class TestComplexEnumConverter implements ConfigConverter<TestComplexEnum> {
        @Override
        public TestComplexEnum convert(String value) throws IllegalArgumentException, NullPointerException {
            return TestComplexEnum.ONE;
        }
    }

    enum TestPlainEnum {
        UNO
    }

    enum TestComplexEnum {
        ONE(1);
        final int value;

        TestComplexEnum(int value) {
            this.value = value;
        }
    }
}
