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

package com.hedera.services.yahcli.commands.contract.evminfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.hedera.services.yahcli.commands.contract.utils.Range;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class RangeTest {

    @Test()
    void emptyRangeTest() {
        {
            final var sut = new Range<RangeTest>(0, 0);
            assertAll(
                    "canonical empty",
                    () -> assertThat(sut.length()).isZero(),
                    () -> assertThat(sut.isEmpty()).isTrue(),
                    () -> assertThat(sut.last()).isZero(),
                    () -> assertThat(sut.onePastEnd()).isZero(),
                    () -> assertThat(sut.canonicalize()).isEqualTo(sut),
                    () -> assertThatCode(sut::validate).doesNotThrowAnyException(),
                    () -> assertThat(sut).hasToString("Range[0x0000,0x0000)"),
                    () -> assertThat(sut.toString("Byte", 10)).isEqualTo("Range<Byte>[0,0)"));
        }
        {
            final var sut = new Range<RangeTest>(32, 32);
            assertAll(
                    "non-canonical empty (zero-length but non-zero base)",
                    () -> assertThat(sut.length()).isZero(),
                    () -> assertThat(sut.isEmpty()).isTrue(),
                    () -> assertThat(sut.last()).isZero(),
                    () -> assertThat(sut.onePastEnd()).isEqualTo(32),
                    () -> assertThat(sut.canonicalize()).isEqualTo(Range.<RangeTest>empty()),
                    () -> assertThatCode(sut::validate).doesNotThrowAnyException(),
                    () -> assertThat(sut).hasToString("Range[0x0020,0x0020)"),
                    () -> assertThat(sut.toString("Byte", 10)).isEqualTo("Range<Byte>[32,32)"));
        }
    }

    @Test
    void nonEmptyRangeTests() {
        {
            final var sut = new Range<RangeTest>(0, 10);
            assertAll(
                    "0-based positive length",
                    () -> assertThat(sut.length()).isEqualTo(10),
                    () -> assertThat(sut.isEmpty()).isFalse(),
                    () -> assertThat(sut.last()).isEqualTo(9),
                    () -> assertThat(sut.onePastEnd()).isEqualTo(10),
                    () -> assertThat(sut.canonicalize()).isEqualTo(sut),
                    () -> assertThatCode(sut::validate).doesNotThrowAnyException(),
                    () -> assertThat(sut).hasToString("Range[0x0000,0x000A)"),
                    () -> assertThat(sut.toString("Byte", 10)).isEqualTo("Range<Byte>[0,10)"));
        }

        {
            final var sut = new Range<RangeTest>(10, 26);
            assertAll(
                    "positive-based positive length",
                    () -> assertThat(sut.length()).isEqualTo(16),
                    () -> assertThat(sut.isEmpty()).isFalse(),
                    () -> assertThat(sut.last()).isEqualTo(25),
                    () -> assertThat(sut.onePastEnd()).isEqualTo(26),
                    () -> assertThat(sut.canonicalize()).isEqualTo(sut),
                    () -> assertThatCode(sut::validate).doesNotThrowAnyException(),
                    () -> assertThat(sut).hasToString("Range[0x000A,0x001A)"),
                    () -> assertThat(sut.toString("Byte", 10)).isEqualTo("Range<Byte>[10,26)"));
        }
    }

    @Test
    void invalidRangeTests() {
        assertAll(
                "invalid ranges",
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new Range<RangeTest>(-10, 20).validate()),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new Range<RangeTest>(0, -1).validate()),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new Range<RangeTest>(10, 5).validate()));
    }

    @Test
    void boundaryTests() {
        record TC(
                int from,
                int to,
                int point,
                boolean expectedContains,
                boolean expectedAtEnd,
                boolean expectedProperlyWithin) {}

        final TC[] testCases = {
            new TC(1, 10, 3, true, false, true),
            new TC(1, 10, 1, true, true, false),
            new TC(1, 10, 9, true, true, false),
            new TC(1, 10, 10, false, false, false),
            new TC(1, 2, 1, true, true, false),
            new TC(1, 2, 2, false, false, false),
        };

        final var containsAsserts = new ArrayList<Executable>();
        final var atAnEndAsserts = new ArrayList<Executable>();
        final var properlyWithinAsserts = new ArrayList<Executable>();

        for (int n = 0; n < testCases.length; n++) {
            final var tc = testCases[n];
            final var descr = "case %d".formatted(n);
            containsAsserts.add(() -> assertThat(new Range<RangeTest>(tc.from, tc.to).contains(tc.point))
                    .as(descr)
                    .isEqualTo(tc.expectedContains));
            atAnEndAsserts.add(() -> assertThat(new Range<RangeTest>(tc.from, tc.to).atAnEnd(tc.point))
                    .as(descr)
                    .isEqualTo(tc.expectedAtEnd));
            properlyWithinAsserts.add(() -> assertThat(new Range<RangeTest>(tc.from, tc.to).properlyWithin(tc.point))
                    .as(descr)
                    .isEqualTo(tc.expectedProperlyWithin));
        }

        assertAll("contains", containsAsserts);
        assertAll("atAnEnd", atAnEndAsserts);
        assertAll("properlyWithin", properlyWithinAsserts);
    }

    private static @NonNull List<Range<RangeTest>> makeRanges(@NonNull int[] fromToPairs) {
        if (0 != fromToPairs.length % 2)
            throw new IllegalArgumentException("must have even number of ints to make from-to pairs");

        final var r = new ArrayList<Range<RangeTest>>(fromToPairs.length / 2);
        for (int i = 0; i < fromToPairs.length; i += 2) {
            r.add(new Range<RangeTest>(fromToPairs[i], fromToPairs[i + 1]));
        }
        return r;
    }

    @Test
    void hasOverlappingRangesTest() {

        record TC(@NonNull List<Range<RangeTest>> lines, boolean expected) {
            TC(boolean expected, int... fromToPairs) {
                this(makeRanges(fromToPairs), expected);
            }
        }

        final TC[] testCases = {
            new TC(false),
            new TC(false, 0, 10),
            new TC(false, 0, 5, 10, 15),
            new TC(false, 0, 5, 5, 15),
            new TC(false, 2, 5, 5, 15, 20, 25),
            new TC(false, 2, 5, 6, 15, 15, 25),
            new TC(false, 0, 5, 5, 6),
            new TC(false, 0, 5, 10, 15, 6, 7),
            new TC(false, 12, 13, 13, 14),
            new TC(true, 0, 1, 0, 10),
            new TC(true, 0, 10, 0, 1),
            new TC(true, 0, 10, 9, 10),
            new TC(true, 0, 10, 9, 20),
            new TC(true, 0, 10, 2, 3),
            new TC(true, 0, 10, 2, 4),
            new TC(true, 12, 13, 12, 13),
        };

        final var hasOverlappingRangesAsserts = new ArrayList<Executable>(testCases.length);
        int n = 0;
        for (final var tc : testCases) {
            final int nn = n;
            hasOverlappingRangesAsserts.add(() -> assertThat(Range.hasOverlappingRanges(tc.lines))
                    .as("expected %s case %d: %s".formatted(tc.expected, nn, tc.lines))
                    .isEqualTo(tc.expected));

            n++;
        }
        assertAll("hasOverlappingRanges tests", hasOverlappingRangesAsserts);
    }
}
