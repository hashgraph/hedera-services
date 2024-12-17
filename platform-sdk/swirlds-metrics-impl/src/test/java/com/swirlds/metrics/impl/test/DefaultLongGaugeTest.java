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

package com.swirlds.metrics.impl.test;

import static com.swirlds.metrics.api.Metric.ValueType.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.metrics.api.IntegerGauge;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.snapshot.Snapshot.SnapshotEntry;
import com.swirlds.metrics.impl.DefaultIntegerGauge;
import com.swirlds.metrics.impl.DefaultLongGauge;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultLongGaugeTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String UNIT = "UnIt";
    private static final String FORMAT = "FoRmAt";

    @Test
    @DisplayName("Constructor should store values")
    void testConstructor() {
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT)
                .withInitialValue(42L);
        final LongGauge gauge = new DefaultLongGauge(config);

        assertEquals(CATEGORY, gauge.getCategory(), "The category was not set correctly in the constructor");
        assertEquals(NAME, gauge.getName(), "The name was not set correctly in the constructor");
        assertEquals(DESCRIPTION, gauge.getDescription(), "The description was not set correctly in the constructor");
        assertEquals(UNIT, gauge.getUnit(), "The unit was not set correctly in the constructor");
        assertEquals(FORMAT, gauge.getFormat(), "The format was not set correctly in the constructor");
        assertEquals(42L, gauge.get(), "The value was not initialized correctly");
        assertEquals(42L, gauge.get(VALUE), "The value was not initialized correctly");
        assertThat(gauge.getValueTypes()).containsExactly(VALUE);
    }

    @Test
    @DisplayName("Test of get() and set()-operation")
    void testGetAndSet() {
        // given
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME).withInitialValue(2L);
        final LongGauge gauge = new DefaultLongGauge(config);

        // when
        gauge.set(5L);

        // then
        assertEquals(5L, gauge.get(), "Value should be 5");
        assertEquals(5L, gauge.get(VALUE), "Value should be 5");

        // when
        gauge.set(-3L);

        // then
        assertEquals(-3L, gauge.get(), "Value should be -3");
        assertEquals(-3L, gauge.get(VALUE), "Value should be -3");
    }

    @Test
    void testSnapshot() {
        // given
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME).withInitialValue(2L);
        final DefaultLongGauge gauge = new DefaultLongGauge(config);

        // when
        final List<SnapshotEntry> snapshot = gauge.takeSnapshot();

        // then
        assertEquals(2L, gauge.get(), "Value should be 2");
        assertEquals(2L, gauge.get(VALUE), "Value should be 2");
        assertThat(snapshot).containsExactly(new SnapshotEntry(VALUE, 2L));
    }

    @Test
    void testInvalidGets() {
        // given
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME);
        final LongGauge gauge = new DefaultLongGauge(config);

        // then
        assertThrows(NullPointerException.class, () -> gauge.get(null), "Calling get() with null should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> gauge.get(Metric.ValueType.MIN),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> gauge.get(Metric.ValueType.MAX),
                "Calling get() with an unsupported MetricType should throw an IAE");
        assertThrows(
                IllegalArgumentException.class,
                () -> gauge.get(Metric.ValueType.STD_DEV),
                "Calling get() with an unsupported MetricType should throw an IAE");
    }

    @Test
    void testReset() {
        // given
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME);
        final LongGauge gauge = new DefaultLongGauge(config);

        // then
        assertThatCode(gauge::reset).doesNotThrowAnyException();
    }

    @Test
    void testEquals() {
        // given
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME);
        final LongGauge gauge1 = new DefaultLongGauge(config);
        final LongGauge gauge2 = new DefaultLongGauge(config);
        gauge2.set(42L);

        // then
        assertThat(gauge1)
                .isEqualTo(gauge2)
                .hasSameHashCodeAs(gauge2)
                .isNotEqualTo(new DefaultLongGauge(new LongGauge.Config("Other", NAME)))
                .isNotEqualTo(new DefaultLongGauge(new LongGauge.Config(CATEGORY, "Other")))
                .isNotEqualTo(new DefaultIntegerGauge(new IntegerGauge.Config(CATEGORY, NAME)));
    }

    @Test
    void testToString() {
        // given
        final LongGauge.Config config = new LongGauge.Config(CATEGORY, NAME)
                .withDescription(DESCRIPTION)
                .withUnit(UNIT)
                .withFormat(FORMAT)
                .withInitialValue(42L);
        final LongGauge gauge = new DefaultLongGauge(config);

        // then
        assertThat(gauge.toString())
                .contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, Metric.DataType.INT.toString(), "42");
    }
}
