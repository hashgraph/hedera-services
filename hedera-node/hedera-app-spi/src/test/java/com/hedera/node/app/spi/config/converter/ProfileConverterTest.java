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

import com.hedera.node.app.spi.config.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfileConverterTest {

    @Test
    void testNullParam() {
        // given
        final ProfileConverter converter = new ProfileConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidParam() {
        // given
        final ProfileConverter converter = new ProfileConverter();

        // then
        assertThatThrownBy(() -> converter.convert("null")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @ParameterizedTest
    @CsvSource({
        "DEV, DEV",
        "TEST, TEST",
        "PROD, PROD",
        "dev, DEV",
        "test, TEST",
        "prod, PROD",
        "dEv, DEV",
        "tEst, TEST",
        "pRod, PROD",
        "0,DEV",
        "2,TEST",
        "1,PROD"
    })
    void testValidParam(final String input, final String enumName) {
        // given
        final ProfileConverter converter = new ProfileConverter();
        final Profile expected = Profile.valueOf(enumName);

        // when
        final Profile cryptoTransfer = converter.convert(input);

        // then
        assertThat(cryptoTransfer).isEqualTo(expected);
    }
}
