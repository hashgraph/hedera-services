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

package com.hedera.node.app.service.mono.stats;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.stream.proto.SidecarType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SidecarInstrumentationImplTest {

    static final Long M = Long.MIN_VALUE;

    // Match `LongSampleAccumulator.toString()`
    @NonNull
    static String expectedAccumulator(
            @NonNull String name,
            @NonNull String type,
            @NonNull String units,
            int n,
            long total,
            long max,
            long denom) {
        final String expectedFormat = """
                %s=%s%s[#%d, ∑%d, 𝑚𝑎𝑥 %s, ÷%d]\
                """;
        final var maxs = max == Long.MIN_VALUE ? "MINIMUM" : Long.toString(max, 10);
        return expectedFormat.formatted(name, type, units, n, total, maxs, denom);
    }

    // Match `SidecarAccumulators.toString()`
    @NonNull
    static String expectedAccumulators(
            @NonNull String type,
            int nss, // quad for "serializedSize" == "ss"
            long tss,
            long mss,
            long dss,
            int ncd, // quad for "computeDuration" == "cd"
            long tcd,
            long mcd,
            long dcd,
            int nds, // quad for "durationSplit" == "ds"
            long tds,
            long mds,
            long dds) {
        final var ss = expectedAccumulator("serializedSize", type, "SizeBytes", nss, tss, mss, dss);
        final var cd = expectedAccumulator("computeDuration", type, "DurationMs", ncd, tcd, mcd, dcd);
        final var ds = expectedAccumulator("durationSplit", type, "DurationSplitMs", nds, tds, mds, dds);
        return "SidecarAccumulators[%s, %s, %s]".formatted(ss, cd, ds);
    }

    // limited escaping for regexp just suited for our needs here - left only brackets need to be quoted
    @NonNull
    static String escapeForRegexp(@NonNull String re) {
        return re.replace("[", "\\[");
    }

    @Test
    void constructorPassingInClockTest() {
        final Clock clock = Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(1));
        final SidecarInstrumentationImpl sut = new SidecarInstrumentationImpl(clock);
        assertEquals(clock, sut.getClock());
    }

    @Test
    void addSampleSizeTest() {

        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();

        long sample1 = 100L;
        sut.addSample(SidecarType.CONTRACT_BYTECODE, sample1);

        //        """
        //        SidecarAccumulators\\[\
        //        serializedSize=CONTRACT_BYTECODESizeBytes\\[#1, ∑100, 𝑚𝑎𝑥 100, ÷1], \
        //        computeDuration=CONTRACT_BYTECODEDurationMs\\[#0, ∑0, 𝑚𝑎𝑥 MINIMUM, ÷1000000], \
        //        durationSplit=CONTRACT_BYTECODEDurationSplitMs\\[#0, ∑0, 𝑚𝑎𝑥 MINIMUM, ÷1000000]]\
        //        """;

        final var expected1 =
                expectedAccumulators("CONTRACT_BYTECODE", 1, 100, 100, 1, 0, 0, M, 1000000, 0, 0, M, 1000000);
        assertEquals(expected1, sut.toString(SidecarType.CONTRACT_BYTECODE));

        long sample2 = 233L;
        sut.addSample(SidecarType.CONTRACT_BYTECODE, sample2);

        final var expected2 =
                expectedAccumulators("CONTRACT_BYTECODE", 2, 333, 233, 1, 0, 0, M, 1000000, 0, 0, M, 1000000);
        assertEquals(expected2, sut.toString(SidecarType.CONTRACT_BYTECODE));
    }

    @Test
    void addSampleDurationTest() {

        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();

        Duration sample1 = Duration.ofNanos(100L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, sample1);

        final var expected1 =
                expectedAccumulators("CONTRACT_BYTECODE", 0, 0, M, 1, 1, 100, 100, 1000000, 0, 0, M, 1000000);
        assertEquals(expected1, sut.toString(SidecarType.CONTRACT_BYTECODE));

        Duration sample2 = Duration.ofNanos(233L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, sample2);

        final var expected2 =
                expectedAccumulators("CONTRACT_BYTECODE", 0, 0, M, 1, 2, 333, 233, 1000000, 0, 0, M, 1000000);
        assertEquals(expected2, sut.toString(SidecarType.CONTRACT_BYTECODE));
    }

    // A short sleep that will pass a test for having an actual delay
    private void doNothingButExpectTrouble() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(1);
    }

    @Test
    void captureDurationSplitRunnableTest() {

        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();
        final Runnable lambda = () -> {
            try {
                doNothingButExpectTrouble();
            } catch (InterruptedException ex) {
                return;
            }
        };

        // Build a regexp to match a variable number of nanoseconds delay: At least 1ms, and total == max
        final var expectedPreRegexp =
                expectedAccumulators("CONTRACT_BYTECODE", 0, 0, M, 1, 0, 0, M, 1000000, 1, 12345, 54321, 1000000);
        final var expectedRegexp = escapeForRegexp(expectedPreRegexp)
                .replace("12345", "([1-7][0-9]{6})")
                .replace("54321", "\\1");

        sut.captureDurationSplit(SidecarType.CONTRACT_BYTECODE, lambda);

        assertLinesMatch(List.of(expectedRegexp), List.of(sut.toString(SidecarType.CONTRACT_BYTECODE)));
    }

    @Test
    void captureDurationSplitCallableTest() {

        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();
        final SidecarInstrumentation.ExceptionFreeCallable<Integer> lambda = () -> {
            try {
                doNothingButExpectTrouble();
            } catch (InterruptedException ex) {
                return -1;
            }
            return 25_000;
        };

        final Integer expectedReturn = 25_000;

        // Build a regexp to match a variable number of nanoseconds delay: At least 1ms, and total == max
        final var expectedPreRegexp =
                expectedAccumulators("CONTRACT_BYTECODE", 0, 0, M, 1, 0, 0, M, 1000000, 1, 12345, 54321, 1000000);
        final var expectedRegexp = escapeForRegexp(expectedPreRegexp)
                .replace("12345", "([1-7][0-9]{6})")
                .replace("54321", "\\1");

        final Integer actualReturn = sut.captureDurationSplit(SidecarType.CONTRACT_BYTECODE, lambda);

        assertEquals(expectedReturn, actualReturn);
        assertLinesMatch(List.of(expectedRegexp), List.of(sut.toString(SidecarType.CONTRACT_BYTECODE)));
    }

    @Test
    void addSampleSizeAndDurationTest() {

        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();

        long sample1L = 100;
        Duration sample1D = Duration.ofNanos(100L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, sample1L, sample1D);

        final var expected1 =
                expectedAccumulators("CONTRACT_BYTECODE", 1, 100, 100, 1, 1, 100, 100, 1000000, 0, 0, M, 1000000);
        assertEquals(expected1, sut.toString(SidecarType.CONTRACT_BYTECODE));

        long sample2L = 233;
        Duration sample2D = Duration.ofNanos(233L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, sample2L, sample2D);

        final var expected2 =
                expectedAccumulators("CONTRACT_BYTECODE", 2, 333, 233, 1, 2, 333, 233, 1000000, 0, 0, M, 1000000);
        assertEquals(expected2, sut.toString(SidecarType.CONTRACT_BYTECODE));
    }

    @Test
    void addSamplesTest() {

        final SidecarInstrumentation src = new SidecarInstrumentationImpl();
        src.addSample(SidecarType.CONTRACT_BYTECODE, 100L);
        src.addSample(SidecarType.CONTRACT_ACTION, Duration.ofNanos(321L));

        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();
        sut.addSample(SidecarType.CONTRACT_BYTECODE, 55L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, Duration.ofNanos(123L));
        sut.addSample(SidecarType.CONTRACT_STATE_CHANGE, Duration.ofNanos(555L));

        sut.addSamples(src);

        final var actual = String.join(
                "; ",
                sut.toString(SidecarType.CONTRACT_BYTECODE),
                sut.toString(SidecarType.CONTRACT_ACTION),
                sut.toString(SidecarType.CONTRACT_STATE_CHANGE));

        final var expectedBC =
                expectedAccumulators("CONTRACT_BYTECODE", 2, 155, 100, 1, 1, 123, 123, 1000000, 0, 0, M, 1000000);
        final var expectedAC =
                expectedAccumulators("CONTRACT_ACTION", 0, 0, M, 1, 1, 321, 321, 1000000, 0, 0, M, 1000000);
        final var expectedSC =
                expectedAccumulators("CONTRACT_STATE_CHANGE", 0, 0, M, 1, 1, 555, 555, 1000000, 0, 0, M, 1000000);
        final var expected = String.join("; ", expectedBC, expectedAC, expectedSC);
        assertEquals(expected, actual);
    }

    @Test
    void addSamplesDoesNothingIfNotAProperImpl() {

        final SidecarInstrumentation src = SidecarInstrumentation.createNoop();
        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();
        sut.addSample(SidecarType.CONTRACT_BYTECODE, 55L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, Duration.ofNanos(123L));
        sut.addSample(SidecarType.CONTRACT_STATE_CHANGE, Duration.ofNanos(555L));

        sut.addSamples(src);

        final var actual = String.join(
                "; ",
                sut.toString(SidecarType.CONTRACT_BYTECODE),
                sut.toString(SidecarType.CONTRACT_ACTION),
                sut.toString(SidecarType.CONTRACT_STATE_CHANGE));

        final var expectedBC =
                expectedAccumulators("CONTRACT_BYTECODE", 1, 55, 55, 1, 1, 123, 123, 1000000, 0, 0, M, 1000000);
        final var expectedAC = expectedAccumulators("CONTRACT_ACTION", 0, 0, M, 1, 0, 0, M, 1000000, 0, 0, M, 1000000);
        final var expectedSC =
                expectedAccumulators("CONTRACT_STATE_CHANGE", 0, 0, M, 1, 1, 555, 555, 1000000, 0, 0, M, 1000000);
        final var expected = String.join("; ", expectedBC, expectedAC, expectedSC);
        assertEquals(expected, actual);
    }

    @Test
    void captureDurationSplitRunnableOfNoopInstrumentationTest() {
        final SidecarInstrumentation sut = SidecarInstrumentation.createNoop();
        final Runnable lambda = () -> {
            try {
                doNothingButExpectTrouble();
            } catch (InterruptedException ex) {
                return;
            }
        };

        final String expectedStringRegexp = "CONTRACT_BYTECODE\\[INVALID]";

        sut.captureDurationSplit(SidecarType.CONTRACT_BYTECODE, lambda);

        assertLinesMatch(List.of(expectedStringRegexp), List.of(sut.toString(SidecarType.CONTRACT_BYTECODE)));
    }

    @Test
    void captureDurationSplitCallableOfNoopInstrumentationTest() {
        final SidecarInstrumentation sut = SidecarInstrumentation.createNoop();
        final SidecarInstrumentation.ExceptionFreeCallable<Integer> lambda = () -> {
            try {
                doNothingButExpectTrouble();
            } catch (InterruptedException ex) {
                return -1;
            }
            return 25_000;
        };

        final Integer expectedReturn = 25_000;
        final String expectedStringRegexp = "CONTRACT_BYTECODE\\[INVALID]";

        final Integer actualReturn = sut.captureDurationSplit(SidecarType.CONTRACT_BYTECODE, lambda);

        assertEquals(expectedReturn, actualReturn);
        assertLinesMatch(List.of(expectedStringRegexp), List.of(sut.toString(SidecarType.CONTRACT_BYTECODE)));
    }

    @Test
    void toStringOnNoopInstrumentation() {
        final SidecarInstrumentation sut = SidecarInstrumentation.createNoop();
        final String expected = "CONTRACT_BYTECODE[INVALID]";
        final String actual = sut.toString(SidecarType.CONTRACT_BYTECODE);

        assertEquals(expected, actual);
    }

    @Test
    void resetTest() {
        final SidecarInstrumentation sut = new SidecarInstrumentationImpl();

        sut.addSample(SidecarType.CONTRACT_BYTECODE, 100L);
        sut.addSample(SidecarType.CONTRACT_ACTION, Duration.ofNanos(321L));
        sut.addSample(SidecarType.CONTRACT_BYTECODE, 55L);
        sut.addSample(SidecarType.CONTRACT_BYTECODE, Duration.ofNanos(123L));
        sut.addSample(SidecarType.CONTRACT_STATE_CHANGE, Duration.ofNanos(555L));

        sut.reset();

        final var actual = String.join(
                "; ",
                sut.toString(SidecarType.CONTRACT_BYTECODE),
                sut.toString(SidecarType.CONTRACT_ACTION),
                sut.toString(SidecarType.CONTRACT_STATE_CHANGE));

        final var expectedBC =
                expectedAccumulators("CONTRACT_BYTECODE", 0, 0, M, 1, 0, 0, M, 1000000, 0, 0, M, 1000000);
        final var expectedAC = expectedAccumulators("CONTRACT_ACTION", 0, 0, M, 1, 0, 0, M, 1000000, 0, 0, M, 1000000);
        final var expectedSC =
                expectedAccumulators("CONTRACT_STATE_CHANGE", 0, 0, M, 1, 0, 0, M, 1000000, 0, 0, M, 1000000);
        final var expected = String.join("; ", expectedBC, expectedAC, expectedSC);
        assertEquals(expected, actual);
    }

    @Test
    void copyCounterTest() {
        final SidecarInstrumentationImpl sutHidden = new SidecarInstrumentationImpl();
        final SidecarInstrumentation sut = sutHidden;

        assertEquals(0, sutHidden.getCopies());
        sut.addCopy();
        assertEquals(1, sutHidden.getCopies());
        sut.addCopy();
        assertEquals(2, sutHidden.getCopies());
        assertFalse(sut.removeCopy());
        assertTrue(sut.removeCopy());
    }

    @Test
    void orderByHashcodeTest() {
        final Object a = new SidecarInstrumentationImpl();
        final Object b = new SidecarInstrumentationImpl();

        assertNotEquals(
                SidecarInstrumentationImpl.testOrderByHashcode(a, b),
                SidecarInstrumentationImpl.testOrderByHashcode(b, a));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    SidecarInstrumentationImpl.testOrderByHashcode(a, a);
                })
                .withMessage("arguments are not distinct objects");
    }
}
