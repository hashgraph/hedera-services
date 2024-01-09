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

package com.swirlds.platform.test.event.preconsensus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.platform.event.preconsensus.PcesFile;
import com.swirlds.platform.event.preconsensus.PcesUtilities;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PcesUtilities}
 */
class PcesUtilitiesTests {
    private FakeTime time;
    private PcesFile previousFileDescriptor;

    @BeforeEach
    void setup() {
        time = new FakeTime();
        time.tick(Duration.ofSeconds(100));
        previousFileDescriptor = PcesFile.of(time.now(), 2, 10, 20, 5, Path.of("root"));
    }

    @Test
    @DisplayName("Standard operation")
    void standardOperation() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber() + 1,
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertDoesNotThrow(() -> PcesUtilities.fileSanityChecks(
                false,
                previousFileDescriptor.getSequenceNumber(),
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                previousFileDescriptor.getTimestamp(),
                currentFileDescriptor));
    }

    @Test
    @DisplayName("Decreasing sequence number")
    void decreasingSequenceNumber() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber() - 1,
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertThrows(
                IllegalStateException.class,
                () -> PcesUtilities.fileSanityChecks(
                        false,
                        previousFileDescriptor.getSequenceNumber(),
                        previousFileDescriptor.getMinimumGeneration(),
                        previousFileDescriptor.getMaximumGeneration(),
                        previousFileDescriptor.getOrigin(),
                        previousFileDescriptor.getTimestamp(),
                        currentFileDescriptor));
    }

    @Test
    @DisplayName("Decreasing sequence number with gaps permitted")
    void decreasingSequenceNumberWithGapsPermitted() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber() - 1,
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertDoesNotThrow(() -> PcesUtilities.fileSanityChecks(
                true,
                previousFileDescriptor.getSequenceNumber(),
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                previousFileDescriptor.getTimestamp(),
                currentFileDescriptor));
    }

    @Test
    @DisplayName("Non-increasing sequence number")
    void nonIncreasingSequenceNumber() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber(),
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertThrows(
                IllegalStateException.class,
                () -> PcesUtilities.fileSanityChecks(
                        false,
                        previousFileDescriptor.getSequenceNumber(),
                        previousFileDescriptor.getMinimumGeneration(),
                        previousFileDescriptor.getMaximumGeneration(),
                        previousFileDescriptor.getOrigin(),
                        previousFileDescriptor.getTimestamp(),
                        currentFileDescriptor));
    }

    @Test
    @DisplayName("Decreasing minimum generation")
    void decreasingMinimumGeneration() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber() + 1,
                previousFileDescriptor.getMinimumGeneration() - 1,
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertThrows(
                IllegalStateException.class,
                () -> PcesUtilities.fileSanityChecks(
                        false,
                        previousFileDescriptor.getSequenceNumber(),
                        previousFileDescriptor.getMinimumGeneration(),
                        previousFileDescriptor.getMaximumGeneration(),
                        previousFileDescriptor.getOrigin(),
                        previousFileDescriptor.getTimestamp(),
                        currentFileDescriptor));
    }

    @Test
    @DisplayName("Decreasing maximum generation")
    void decreasingMaximumGeneration() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber() + 1,
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration() - 1,
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertThrows(
                IllegalStateException.class,
                () -> PcesUtilities.fileSanityChecks(
                        false,
                        previousFileDescriptor.getSequenceNumber(),
                        previousFileDescriptor.getMinimumGeneration(),
                        previousFileDescriptor.getMaximumGeneration(),
                        previousFileDescriptor.getOrigin(),
                        previousFileDescriptor.getTimestamp(),
                        currentFileDescriptor));
    }

    @Test
    @DisplayName("Decreasing timestamp")
    void decreasingTimestamp() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                previousFileDescriptor.getTimestamp().minusSeconds(10),
                previousFileDescriptor.getSequenceNumber() + 1,
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin(),
                Path.of("root"));

        assertThrows(
                IllegalStateException.class,
                () -> PcesUtilities.fileSanityChecks(
                        false,
                        previousFileDescriptor.getSequenceNumber(),
                        previousFileDescriptor.getMinimumGeneration(),
                        previousFileDescriptor.getMaximumGeneration(),
                        previousFileDescriptor.getOrigin(),
                        previousFileDescriptor.getTimestamp(),
                        currentFileDescriptor));
    }

    @Test
    @DisplayName("Decreasing origin")
    void decreasingOrigin() {
        final PcesFile currentFileDescriptor = PcesFile.of(
                time.now(),
                previousFileDescriptor.getSequenceNumber() + 1,
                previousFileDescriptor.getMinimumGeneration(),
                previousFileDescriptor.getMaximumGeneration(),
                previousFileDescriptor.getOrigin() - 1,
                Path.of("root"));

        assertThrows(
                IllegalStateException.class,
                () -> PcesUtilities.fileSanityChecks(
                        false,
                        previousFileDescriptor.getSequenceNumber(),
                        previousFileDescriptor.getMinimumGeneration(),
                        previousFileDescriptor.getMaximumGeneration(),
                        previousFileDescriptor.getOrigin(),
                        previousFileDescriptor.getTimestamp(),
                        currentFileDescriptor));
    }
}
