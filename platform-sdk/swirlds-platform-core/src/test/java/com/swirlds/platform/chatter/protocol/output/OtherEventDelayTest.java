/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.protocol.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OtherEventDelayTest {
    public static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(null, 1, 1, null, "delay is unknown if processing time is unknown"),
                Arguments.of(1, null, 1, null, "delay is unknown if round trip time is unknown"),
                Arguments.of(8, 2, 3, 9, "8/2 + 2 + 3 == 9"));
    }

    @ParameterizedTest
    @MethodSource("params")
    void processingTimeNull(
            final Integer heartbeatRoundTrip,
            final Integer peerProcessingTime,
            final Integer constantDelay,
            final Integer expectedResult,
            final String message) {

        final OtherEventDelay otherEventDelay = new OtherEventDelay(
                () -> toLong(heartbeatRoundTrip), () -> toLong(peerProcessingTime), ofNanos(constantDelay));
        assertEquals(ofNanos(expectedResult), otherEventDelay.getOtherEventDelay(), message);
    }

    private static Duration ofNanos(final Integer i) {
        return i == null ? null : Duration.ofNanos(i);
    }

    private static Long toLong(final Integer i) {
        return i == null ? null : Long.valueOf(i);
    }
}
