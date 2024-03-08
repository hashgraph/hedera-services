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

package com.swirlds.base.sample.metrics;

import com.swirlds.base.sample.internal.Context;
import com.swirlds.common.metrics.FunctionGauge;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Registers JVM metrics with the purpose of Benchmarking app behaviour
 */
public class BenchmarkMetrics {

    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();
    private static final BufferPoolMXBean DIRECT_MEM_MX_BEAN = getDirectMemMxBean();
    static final String BENCHMARK_CATEGORY = "internal";
    private static final FunctionGauge.Config<String> TIMESTAMP_CONFIG = new FunctionGauge.Config<>(
                    BENCHMARK_CATEGORY, "time", String.class, () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                            .format(Instant.now().atZone(ZoneId.of("UTC"))))
            .withDescription("the current time")
            .withFormat("%24s");

    static final String FORMAT_INTEGER = " %d";
    private static final FunctionGauge.Config<Long> MEM_TOT_CONFIG = new FunctionGauge.Config<>(
                    BENCHMARK_CATEGORY, "memTot", Long.class, Runtime.getRuntime()::totalMemory)
            .withDescription("total bytes in the JVM heap")
            .withFormat(FORMAT_INTEGER);

    private static final FunctionGauge.Config<Long> MEM_FREE_CONFIG = new FunctionGauge.Config<>(
                    BENCHMARK_CATEGORY, "memFree", Long.class, Runtime.getRuntime()::freeMemory)
            .withDescription("free bytes in the JVM heap")
            .withFormat(FORMAT_INTEGER);

    private static final FunctionGauge.Config<Long> DIRECT_MEM_CONFIG = new FunctionGauge.Config<>(
                    BENCHMARK_CATEGORY,
                    "directMem",
                    Long.class,
                    DIRECT_MEM_MX_BEAN != null ? DIRECT_MEM_MX_BEAN::getMemoryUsed : () -> -1L)
            .withDescription("used bytes of the JVM direct memory")
            .withFormat(FORMAT_INTEGER);

    static final String FORMAT_FLOAT1 = " %.1f";
    private static final FunctionGauge.Config<Double> CPU_LOAD_PROC_CONFIG = new FunctionGauge.Config<>(
                    BENCHMARK_CATEGORY,
                    "cpuLoadProc",
                    Double.class,
                    () -> OS_BEAN instanceof com.sun.management.OperatingSystemMXBean sunBean
                            ? sunBean.getProcessCpuLoad() * Runtime.getRuntime().availableProcessors()
                            : -1.0)
            .withDescription("CPU load of the JVM process")
            .withFormat(FORMAT_FLOAT1);

    private static final FunctionGauge.Config<Long> OPEN_FDS_CONFIG = new FunctionGauge.Config<>(
                    BENCHMARK_CATEGORY,
                    "openFileDesc",
                    Long.class,
                    () -> OS_BEAN instanceof com.sun.management.UnixOperatingSystemMXBean unixBean
                            ? unixBean.getOpenFileDescriptorCount()
                            : -1L)
            .withDescription("Open file descriptors")
            .withFormat(FORMAT_INTEGER);

    public static void registerMetrics(Context context) {
        context.metrics().getOrCreate(TIMESTAMP_CONFIG);
        context.metrics().getOrCreate(MEM_TOT_CONFIG);
        context.metrics().getOrCreate(MEM_FREE_CONFIG);
        context.metrics().getOrCreate(DIRECT_MEM_CONFIG);
        context.metrics().getOrCreate(CPU_LOAD_PROC_CONFIG);
        context.metrics().getOrCreate(OPEN_FDS_CONFIG);
    }

    private static @Nullable BufferPoolMXBean getDirectMemMxBean() {
        final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (final BufferPoolMXBean pool : pools) {
            if (pool.getName().equals("direct")) {
                return pool;
            }
        }
        return null;
    }
}
