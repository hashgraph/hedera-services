/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountIDConverterTest {

    @Test
    void testNullParam() {
        //given
        final AccountIDConverter converter = new AccountIDConverter();

        //then
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  ", "a.b.b",
            "1.b.c", "1.2.c", "a.2.3",
            "1", "1.2",
            ".1.2.3", "..1.2.3", ".1.2.3.", "1.2.3.4"})
    void testAllNotParseable(final String input) {
        //given
        final AccountIDConverter converter = new AccountIDConverter();

        //then
        assertThatThrownBy(() -> converter.convert(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"1.2.3.", "1.2.3.."})
    void testEdgeCasesForDiscussion(final String input) {
        //given
        final AccountIDConverter converter = new AccountIDConverter();

        //then
        assertThatThrownBy(() -> converter.convert(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSimpleValue() {
        //given
        final AccountIDConverter converter = new AccountIDConverter();
        final String value = "1.2.3";

        //when
        final var result = converter.convert(value);

        //then
        assertThat(result).isNotNull();
        assertThat(result.shardNum()).isEqualTo(1L);
        assertThat(result.realmNum()).isEqualTo(2L);
        assertThat(result.accountNum()).isEqualTo(3L);
    }

    @Test
    void testLongValues() {
        //given
        final AccountIDConverter converter = new AccountIDConverter();
        final String value = Long.MAX_VALUE + "." + Long.MAX_VALUE + ".0";

        //when
        final var result = converter.convert(value);

        //then
        assertThat(result).isNotNull();
        assertThat(result.shardNum()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.realmNum()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.accountNum()).isEqualTo(0);
    }
}