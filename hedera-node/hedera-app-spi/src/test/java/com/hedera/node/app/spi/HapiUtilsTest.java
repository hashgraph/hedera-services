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

package com.hedera.node.app.spi;

import static com.hedera.node.app.spi.HapiUtils.asTimestamp;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class HapiUtilsTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            2007-12-03T10:15:30.00Z, 2007-12-03T10:15:30.01Z
            2007-12-31T23:59:59.99Z, 2008-01-01T00:00:00.00Z
            """)
    @DisplayName("When timestamp t1 comes before timestamp t2")
    void isBefore(@NonNull final Instant i1, @NonNull final Instant i2) {
        final var t1 = asTimestamp(i1);
        final var t2 = asTimestamp(i2);
        assertThat(HapiUtils.isBefore(t1, t2)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            2007-12-03T10:15:30.01Z, 2007-12-03T10:15:30.00Z
            2008-01-01T00:00:00.00Z, 2007-12-31T23:59:59.99Z
            """)
    @DisplayName("When timestamp t1 comes after timestamp t2")
    void isAfter(@NonNull final Instant i1, @NonNull final Instant i2) {
        final var t1 = asTimestamp(i1);
        final var t2 = asTimestamp(i2);
        assertThat(HapiUtils.isBefore(t1, t2)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            2007-12-03T10:15:30.00Z, 2007-12-03T10:15:30.00Z
            2007-12-31T23:59:59.99Z, 2007-12-31T23:59:59.99Z
            2008-01-01T00:00:00.00Z, 2008-01-01T00:00:00.00Z
            """)
    @DisplayName("When timestamp t1 is the same as timestamp t2")
    void isEqual(@NonNull final Instant i1, @NonNull final Instant i2) {
        final var t1 = asTimestamp(i1);
        final var t2 = asTimestamp(i2);
        assertThat(HapiUtils.isBefore(t1, t2)).isFalse();
    }

    @Test
    @DisplayName("Converting an Instant into a Timestamp")
    void convertInstantToTimestamp() {
        // Given an instant with nanosecond precision
        final var instant = Instant.ofEpochSecond(1000, 123456789);
        // When we convert it into a timestamp
        final var timestamp = asTimestamp(instant);
        // Then we find the timestamp matches the original instant
        assertThat(timestamp)
                .isEqualTo(Timestamp.newBuilder().seconds(1000).nanos(123456789).build());
    }
}
