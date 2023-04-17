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

package com.hedera.node.app.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.service.mono.throttling.MapAccessType;
import org.junit.jupiter.api.Test;

class MapAccessTypeConverterTest {

    @Test
    void testNullParam() {
        //given
        final MapAccessTypeConverter converter = new MapAccessTypeConverter();

        //then
        assertThatThrownBy(() -> converter.convert(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidParam() {
        //given
        final MapAccessTypeConverter converter = new MapAccessTypeConverter();

        //then
        assertThatThrownBy(() -> converter.convert("null"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testValidParam() {
        //given
        final MapAccessTypeConverter converter = new MapAccessTypeConverter();

        //when
        final MapAccessType entityType = converter.convert("ACCOUNTS_GET");

        //then
        assertThat(entityType).isEqualTo(MapAccessType.ACCOUNTS_GET);
    }
}