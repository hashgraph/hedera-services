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

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import org.junit.jupiter.api.Test;

class ScaleFactorConverterTest {

    @Test
    void testNullValue() {
        // given
        final ScaleFactorConverter converter = new ScaleFactorConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidValue() {
        // given
        final ScaleFactorConverter converter = new ScaleFactorConverter();
        final String input = "1:3";

        // when
        final ScaleFactor scaleFactor = converter.convert(input);

        // then
        assertThat(scaleFactor).isNotNull();
        assertThat(scaleFactor.numerator()).isEqualTo(1);
        assertThat(scaleFactor.denominator()).isEqualTo(3);
    }

    @Test
    void testInvalidValue() {
        // given
        final ScaleFactorConverter converter = new ScaleFactorConverter();

        // then
        assertThatThrownBy(() -> converter.convert("null")).isInstanceOf(IllegalArgumentException.class);
    }
}
