/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import com.swirlds.common.statistics.StatsBuffered;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultLongAccumulatorTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";
    private static final String FORMAT = "FoRmAt";

    @Test
    @DisplayName("Constructor should store values")
    void testConstructor() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT)
                .withInitialValue(42L);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        assertEquals(CATEGORY, accumulator.getCategory(), "The category was not set correctly in the constructor");
        assertEquals(NAME, accumulator.getName(), "The name was not set correctly in the constructor");
        assertEquals(
                DESCRIPTION, accumulator.getDescription(), "The description was not set correctly in the constructor");
        assertEquals(UNIT, accumulator.getUnit(), "The unit was not set correctly in the constructor");
        assertEquals(FORMAT, accumulator.getFormat(), "The format was not set correctly in the constructor");
        assertEquals(42L, accumulator.getInitialValue(), "The initial value was not initialized correctly");
        assertEquals(42L, accumulator.get(), "The value was not initialized correctly");
        assertEquals(42L, accumulator.get(VALUE), "The value was not initialized correctly");
        assertThat(accumulator.getValueTypes()).containsExactly(VALUE);
    }

    @Test
    @DisplayName("Test of get() and update()-operation")
    void testGetAndUpdate() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME)
                .withAccumulator((op1, op2) -> op1 - op2)
                .withInitialValue(2L);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // when
        accumulator.update(5L);

        // then
        assertEquals(-3L, accumulator.get(), "Value should be -3");
        assertEquals(-3L, accumulator.get(VALUE), "Value should be -3");

        // when
        accumulator.update(3L);

        // then
        assertEquals(-6L, accumulator.get(), "Value should be -6");
        assertEquals(-6L, accumulator.get(VALUE), "Value should be -6");
    }

    @Test
    void testPositiveOverflow() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME)
                .withAccumulator(Long::sum)
                .withInitialValue(Long.MAX_VALUE);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // when
        accumulator.update(1L);

        // then
        final long expected = Long.MAX_VALUE + 1L;
        assertEquals(expected, accumulator.get(), "Value should be the same as if a regular long overflows");
    }

    @Test
    void testNegativeOverflow() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME)
                .withAccumulator(Long::sum)
                .withInitialValue(Long.MIN_VALUE);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // when
        accumulator.update(-1L);

        // then
        final long expected = Long.MIN_VALUE - 1L;
        assertEquals(expected, accumulator.get(), "Value should be the same as if a regular long overflows");
    }

    @Test
    void testSnapshot() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME).withInitialValue(2L);
        final DefaultLongAccumulator accumulator = new DefaultLongAccumulator(config);
        accumulator.update(5L);

        // when
        final List<SnapshotEntry> snapshot = accumulator.takeSnapshot();

        // then
        assertEquals(2L, accumulator.get(), "Value should be 2");
        assertEquals(2L, accumulator.get(VALUE), "Value should be 2");
        assertThat(snapshot).containsExactly(new SnapshotEntry(VALUE, 5L));
    }

    @Test
    void testInvalidGets() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // then
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(null),
                "Calling get() with null should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.MIN),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.MAX),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.get(Metric.ValueType.STD_DEV),
                "Calling get() with an unsupported MetricType should throw an IAE");
    }

    @Test
    void testReset() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // then
        assertThatCode(accumulator::reset).doesNotThrowAnyException();
    }

    @SuppressWarnings("removal")
    @Test
    void testGetStatBuffered() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // when
        final StatsBuffered actual = accumulator.getStatsBuffered();

        // then
        assertThat(actual).isNull();
    }

    @Test
    void testEquals() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME);
        final LongAccumulator accumulator1 = new DefaultLongAccumulator(config);
        final LongAccumulator accumulator2 = new DefaultLongAccumulator(config);
        accumulator2.update(42L);

        // then
        assertThat(accumulator1)
                .isEqualTo(accumulator2)
                .hasSameHashCodeAs(accumulator2)
                .isNotEqualTo(new DefaultLongAccumulator(new LongAccumulator.Config("Other", NAME)))
                .isNotEqualTo(new DefaultLongAccumulator(new LongAccumulator.Config(CATEGORY, "Other")))
                .isNotEqualTo(new DefaultIntegerGauge(new IntegerGauge.Config(CATEGORY, NAME)));
    }

    @Test
    void testToString() {
        // given
        final LongAccumulator.Config config = new LongAccumulator.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT)
                .withInitialValue(42L);
        final LongAccumulator accumulator = new DefaultLongAccumulator(config);

        // then
        assertThat(accumulator.toString())
                .contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, Metric.DataType.INT.toString(), "42");
    }
}
