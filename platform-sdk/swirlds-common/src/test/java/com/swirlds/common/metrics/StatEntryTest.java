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

package com.swirlds.common.metrics;

import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.metrics.MetricType.STAT_ENTRY;
import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.internal.StatsBuffer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing StatEntry")
class StatEntryTest {

    private final StatEntry sut = new StatEntry() {
        @Override
        public StatsBuffered getBuffered() {
            return null;
        }

        @Override
        public Consumer<Double> getReset() {
            return null;
        }

        @Override
        public Supplier<Object> getStatsStringSupplier() {
            return null;
        }

        @Override
        public Supplier<Object> getResetStatsStringSupplier() {
            return null;
        }

        @Override
        public String getCategory() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public DataType getDataType() {
            return null;
        }

        @Override
        public String getUnit() {
            return null;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public void reset() {}

        @Override
        public StatsBuffered getStatsBuffered() {
            return null;
        }
    };

    private final StatEntry sutWithStatsBuffered = new StatEntry() {
        @Override
        public StatsBuffered getBuffered() {
            return new StatsBuffered() {
                @Override
                public StatsBuffer getAllHistory() {
                    return null;
                }

                @Override
                public StatsBuffer getRecentHistory() {
                    return null;
                }

                @Override
                public void reset(double halflife) {}

                @Override
                public double getMean() {
                    return 0;
                }

                @Override
                public double getMax() {
                    return 0;
                }

                @Override
                public double getMin() {
                    return 0;
                }

                @Override
                public double getStdDev() {
                    return 0;
                }
            };
        }

        @Override
        public Consumer<Double> getReset() {
            return null;
        }

        @Override
        public Supplier<Object> getStatsStringSupplier() {
            return null;
        }

        @Override
        public Supplier<Object> getResetStatsStringSupplier() {
            return null;
        }

        @Override
        public String getCategory() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public DataType getDataType() {
            return null;
        }

        @Override
        public String getUnit() {
            return null;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public void reset() {}

        @Override
        public StatsBuffered getStatsBuffered() {
            return null;
        }
    };

    @Test
    void getMetricType() {
        assertThat(sut.getMetricType()).isEqualTo(STAT_ENTRY);
    }

    @Test
    void getValueTypes_ShouldHaveVALUE_WithoutStatsBuffered() {
        assertThat(sut.getValueTypes()).containsExactly(VALUE);
    }

    @Test
    void getValueTypes_ShouldReturnAllTypes_WithStatsBuffered() {
        assertThat(sutWithStatsBuffered.getValueTypes()).containsExactly(VALUE, MAX, MIN, STD_DEV);
    }
}
