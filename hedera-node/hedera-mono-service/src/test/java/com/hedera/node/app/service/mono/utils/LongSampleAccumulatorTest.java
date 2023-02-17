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

package com.hedera.node.app.service.mono.utils;

import static org.junit.jupiter.api.Assertions.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class LongSampleAccumulatorTest {

    @Test
    void invalidDenominatorIsDetected() {
        assertThrows(IllegalArgumentException.class, () -> new LongSampleAccumulator("baz", 0L));
    }

    @Test
    void noSamplesProducesZeroValues() {
        final String name = "foo";
        final long denominator = 100L;
        final var sut = new LongSampleAccumulator(name, denominator);

        verifyNullResult(name, denominator, sut);
    }

    @Test
    void oneSampleIsIdentity() {
        final String name = "foo";
        final long sample = 25_100L;
        final long denominator = 100L;
        final var sut = new LongSampleAccumulator(name, denominator);
        sut.addSample(sample);

        final var values = sut.getAccumulatedValues();
        assertEquals(name, values.name());
        assertEquals(1, values.nSamples());
        assertEquals(sample, values.total());
        assertEquals(sample, values.maximum());
        assertEquals(denominator, values.denominator());

        final var scaledValues = sut.getScaledAccumulatedValues();
        assertEquals(name, scaledValues.name());
        assertEquals(1, scaledValues.nSamples());
        assertEquals((double) sample / (double) denominator, scaledValues.total());
        assertEquals((double) sample / (double) denominator, scaledValues.maximum());

        assertEquals("foo[#1, ∑25100, \uD835\uDC5A\uD835\uDC4E\uD835\uDC65 25100, ÷100]", sut.toString());
    }

    @Test
    void multipleSamplesAreEffective() {

        final String name = "foo";
        final long[] samples = LongStream.range(0, 250).map(v -> v * v).toArray();
        final long nSamples = samples.length;
        assertEquals(250, nSamples);
        final long total = Arrays.stream(samples).sum();
        final long maximum = (long) Math.pow(nSamples - 1, 2);
        final long denominator = 100L;
        final var sut = new LongSampleAccumulator(name, denominator);
        for (final var v : samples) sut.addSample(v);

        final var values = sut.getAccumulatedValues();
        assertEquals(name, values.name());
        assertEquals(nSamples, values.nSamples());
        assertEquals(total, values.total());
        assertEquals(maximum, values.maximum());
        assertEquals(denominator, values.denominator());

        final var scaledValues = sut.getScaledAccumulatedValues();
        assertEquals(name, scaledValues.name());
        assertEquals(nSamples, scaledValues.nSamples());
        assertEquals((double) total / (double) denominator, scaledValues.total());
        assertEquals((double) maximum / (double) denominator, scaledValues.maximum());

        assertEquals("foo[#250, ∑5177125, \uD835\uDC5A\uD835\uDC4E\uD835\uDC65 62001, ÷100]", sut.toString());
    }

    @Test
    void resetWorksAsExpected() {
        final String name = "foo";
        final long sample = 25_100L;
        final long denominator = 100L;
        final var sut = new LongSampleAccumulator(name, denominator);
        sut.addSample(sample);
        sut.reset();

        verifyNullResult(name, denominator, sut);
    }

    private void verifyNullResult(@NonNull String name, long denominator, final LongSampleAccumulator sut) {
        final var values = sut.getAccumulatedValues();
        assertEquals(name, values.name());
        assertEquals(0, values.nSamples());
        assertEquals(0L, values.total());
        assertEquals(Long.MIN_VALUE, values.maximum());
        assertEquals(denominator, values.denominator());

        final var scaledValues = sut.getScaledAccumulatedValues();
        assertEquals(name, scaledValues.name());
        assertEquals(0, scaledValues.nSamples());
        assertEquals(0.0, scaledValues.total());
        assertEquals(-Double.MAX_VALUE, scaledValues.maximum());

        assertEquals("foo[#0, ∑0, \uD835\uDC5A\uD835\uDC4E\uD835\uDC65 MINIMUM, ÷100]", sut.toString());
    }
}
