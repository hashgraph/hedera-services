/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.api.internal.format;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.base.test.fixtures.io.WithSystemError;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

@WithSystemError
public class EpochFormatUtilsTest {

    @Test
    void testTimestampAsString() {
        // given
        long timestamp = Instant.parse("2024-03-06T12:00:00Z").toEpochMilli();

        // when
        String formattedTimestamp = EpochFormatUtils.timestampAsString(timestamp);

        String expectedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS   ")
                .withZone(UTC)
                .format(Instant.ofEpochMilli(timestamp));

        // then
        assertThat(formattedTimestamp.length()).isEqualTo(26);
        assertThat(formattedTimestamp).isEqualTo(expectedTimestamp);
    }

    @Test
    void testTimestampAsStringWithNegativeTimestamp() {
        // given
        long timestamp = -1; // Negative timestamp

        // when
        String formattedTimestamp = EpochFormatUtils.timestampAsString(timestamp);

        // then
        assertThat(formattedTimestamp).isEqualTo("1969-12-31 23:59:59.999   ");
    }

    @Test
    void testNegativeTimestampOverflowed26() {
        // given
        String formattedTimestamp = EpochFormatUtils.timestampAsString(Long.MIN_VALUE);
        // then
        assertThat(formattedTimestamp).isEqualTo("BROKEN-TIMESTAMP          ");
    }

    @Test
    void testPositiveTimestampOverflowed26() {
        // given
        String formattedTimestamp = EpochFormatUtils.timestampAsString(Long.MAX_VALUE);
        // then
        assertThat(formattedTimestamp).isEqualTo("BROKEN-TIMESTAMP          ");
    }
}
