/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.converters;

import java.net.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UrlConverterTest {

    @Test
    public void convertNull() {
        // given
        final UrlConverter converter = new UrlConverter();

        // then
        Assertions.assertThrows(NullPointerException.class, () -> converter.convert(null));
    }

    @Test
    public void convert() {
        // given
        final UrlConverter converter = new UrlConverter();
        final String rawValue = "http://example.net";

        // when
        final URL value = converter.convert(rawValue);

        // then
        Assertions.assertNotNull(value);
        Assertions.assertEquals("http://example.net", value.toString());
    }

    @Test
    public void convertBadUrl() {
        // given
        final UrlConverter converter = new UrlConverter();
        final String rawValue = "{invalid:url}";

        // then
        Assertions.assertThrows(IllegalArgumentException.class, () -> converter.convert(rawValue));
    }
}
