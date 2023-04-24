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

package com.hedera.node.app.spi.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.spi.config.types.KeyValuePair;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyValuePairConverterTest {

    @Test
    void testNullValue() {
        // given
        final KeyValuePairConverter converter = new KeyValuePairConverter();
        final String value = null;

        // then
        assertThatThrownBy(() -> converter.convert(value)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidValue() {
        // given
        final KeyValuePairConverter converter = new KeyValuePairConverter();
        final String value = "key=value";

        // when
        final var result = converter.convert(value);

        // then
        assertThat(result.key()).isEqualTo("key");
        assertThat(result.value()).isEqualTo("value");
    }

    @Test
    void testValidValueMultipleSemicolon() {
        // given
        final KeyValuePairConverter converter = new KeyValuePairConverter();
        final String value = "key=value=stillValue;";

        // when
        final var result = converter.convert(value);

        // then
        assertThat(result.key()).isEqualTo("key");
        assertThat(result.value()).isEqualTo("value=stillValue;");
    }

    @Test
    void testInvalidValueNoSemicolon() {
        // given
        final KeyValuePairConverter converter = new KeyValuePairConverter();
        final String value = "key";

        // then
        assertThatThrownBy(() -> converter.convert(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIncludedInConfig() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new KeyValuePairConverter())
                .withSource(new SimpleConfigSource("testProperty", "key=value"))
                .build();

        // when
        final KeyValuePair value = configuration.getValue("testProperty", KeyValuePair.class);

        // then
        assertThat(value).isNotNull();
        assertThat(value.key()).isEqualTo("key");
        assertThat(value.value()).isEqualTo("value");
    }

    @Test
    void testIncludedInListInConfig() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new KeyValuePairConverter())
                .withSource(new SimpleConfigSource("testProperty", "key=value"))
                .build();

        // when
        final List<KeyValuePair> values = configuration.getValues("testProperty", KeyValuePair.class);

        // then
        assertThat(values).isNotNull().hasSize(1);
        assertThat(values.get(0).key()).isEqualTo("key");
        assertThat(values.get(0).value()).isEqualTo("value");
    }

    @Test
    void testMultipleIncludedInListInConfig() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new KeyValuePairConverter())
                .withSource(new SimpleConfigSource("testProperty", "key1=value1,key2=value2,key3=value3"))
                .build();

        // when
        final List<KeyValuePair> values = configuration.getValues("testProperty", KeyValuePair.class);

        // then
        assertThat(values).isNotNull().hasSize(3);
        assertThat(values.get(0).key()).isEqualTo("key1");
        assertThat(values.get(0).value()).isEqualTo("value1");
        assertThat(values.get(1).key()).isEqualTo("key2");
        assertThat(values.get(1).value()).isEqualTo("value2");
        assertThat(values.get(2).key()).isEqualTo("key3");
        assertThat(values.get(2).value()).isEqualTo("value3");
    }
}
