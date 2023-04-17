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

package com.hedera.node.app.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.hapi.utils.sysfiles.domain.KnownBlockValues;
import org.junit.jupiter.api.Test;

class KnownBlockValuesConverterTest {

    @Test
    void testNullValue() {
        // given
        final KnownBlockValuesConverter converter = new KnownBlockValuesConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidValue() {
        // given
        final KnownBlockValuesConverter converter = new KnownBlockValuesConverter();
        final String input = "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666";

        // when
        final KnownBlockValues knownBlockValues = converter.convert(input);

        // then
        assertThat(knownBlockValues).isNotNull();
        assertThat(knownBlockValues.number()).isEqualTo(666L);
        assertThat(knownBlockValues.hash())
                .asHexString()
                .isEqualTo("C9E37A7A454638CA62662BD1A06DE49EF40B3444203FE329BBC81363604EA7F8");
    }

    @Test
    void testInvalidValue() {
        // given
        final KnownBlockValuesConverter converter = new KnownBlockValuesConverter();

        // then
        assertThatThrownBy(() -> converter.convert("null")).isInstanceOf(IllegalArgumentException.class);
    }
}
