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

package com.hedera.node.app.spi.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

class AbstractEnumConfigConverterTest {

    /**
     * Based on https://github.com/hashgraph/hedera-services/issues/6106 we currently need to add ConfigConverter
     * explicitly
     */
    private static class RetentionPolicyConverter extends AbstractEnumConfigConverter<RetentionPolicy>
            implements ConfigConverter<RetentionPolicy> {

        @Override
        protected Class<RetentionPolicy> getEnumType() {
            return RetentionPolicy.class;
        }
    }

    /**
     * Based on https://github.com/hashgraph/hedera-services/issues/6106 we currently need to add ConfigConverter
     * explicitly
     */
    private static class ElementTypeConverter extends AbstractEnumConfigConverter<ElementType>
            implements ConfigConverter<ElementType> {

        @Override
        protected Class<ElementType> getEnumType() {
            return ElementType.class;
        }
    }

    @Test
    void testNullValue() {
        // given
        final RetentionPolicyConverter converter = new RetentionPolicyConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidValue() {
        // given
        final RetentionPolicyConverter converter = new RetentionPolicyConverter();

        // then
        assertThatThrownBy(() -> converter.convert("not-supported-value")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValidValue() {
        // given
        final RetentionPolicyConverter converter = new RetentionPolicyConverter();

        // when
        final RetentionPolicy source = converter.convert("SOURCE");

        // then
        assertThat(source).isEqualTo(RetentionPolicy.SOURCE);
    }

    @Test
    void testIntegration() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("retention-policy", "SOURCE"))
                .withSource(new SimpleConfigSource("element-type", "TYPE"))
                .withConverter(new RetentionPolicyConverter())
                .withConverter(new ElementTypeConverter())
                .build();

        // when
        final RetentionPolicy source = configuration.getValue("retention-policy", RetentionPolicy.class);
        final ElementType type = configuration.getValue("element-type", ElementType.class);

        // then
        assertThat(source).isEqualTo(RetentionPolicy.SOURCE);
        assertThat(type).isEqualTo(ElementType.TYPE);
    }
}
