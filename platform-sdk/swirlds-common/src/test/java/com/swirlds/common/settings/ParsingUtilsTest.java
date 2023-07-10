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

package com.swirlds.common.settings;

import static com.swirlds.common.settings.ParsingUtils.parseDuration;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ParsingUtilsTest {
    @Test
    void defaultUnit() {
        // given
        final String str = "1000";

        // when
        final Duration parsed = parseDuration(str);

        // then
        assertEquals(1000, parsed.toMillis());
    }

    @Test
    void parseDurationMs() {
        // given
        final String str = "1000ms";

        // when
        final Duration parsed = parseDuration(str);

        // then
        assertEquals(1000, parsed.toMillis());
    }

    @Test
    void parseDurationDefaultParser() {
        // given
        final String str = "PT15M";

        // when
        final Duration parsed = parseDuration(str);

        // then
        assertEquals(15, parsed.toMinutes());
    }
}
