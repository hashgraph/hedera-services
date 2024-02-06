package com.swirlds.baseapi.metrics;

import static com.swirlds.baseapi.metrics.MetricsConstants.BENCHMARK_CATEGORY;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BenchmarkMetrics {

    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private static final BufferPoolMXBean directMemMXBean = getDirectMemMXBean();
    private static final FunctionGauge.Config<String> TIMESTAMP_CONFIG = new FunctionGauge.Config<>(
            BENCHMARK_CATEGORY, "time", String.class,
            () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .format(Instant.now().atZone(ZoneId.of("UTC"))))
            .withDescription("the current time")
            .withFormat("%24s");

    private static final FunctionGauge.Config<Long> MEM_TOT_CONFIG = new FunctionGauge.Config<>(
            BENCHMARK_CATEGORY, "memTot", Long.class, Runtime.getRuntime()::totalMemory)
            .withDescription("total bytes in the JVM heap")
            .withFormat(MetricsConstants.FORMAT_INTEGER);

    private static final FunctionGauge.Config<Long> MEM_FREE_CONFIG = new FunctionGauge.Config<>(
            BENCHMARK_CATEGORY, "memFree", Long.class, Runtime.getRuntime()::freeMemory)
            .withDescription("free bytes in the JVM heap")
            .withFormat(MetricsConstants.FORMAT_INTEGER);


    private static final FunctionGauge.Config<Long> DIRECT_MEM_CONFIG = new FunctionGauge.Config<>(
            BENCHMARK_CATEGORY,
            "directMem",
            Long.class,
            directMemMXBean != null ? directMemMXBean::getMemoryUsed : () -> -1L)
            .withDescription("used bytes of the JVM direct memory")
            .withFormat(MetricsConstants.FORMAT_INTEGER);


    private static final FunctionGauge.Config<Double> CPU_LOAD_PROC_CONFIG = new FunctionGauge.Config<>(
            BENCHMARK_CATEGORY,
            "cpuLoadProc",
            Double.class,
            () -> osBean instanceof com.sun.management.OperatingSystemMXBean sunBean
                    ? sunBean.getProcessCpuLoad() * Runtime.getRuntime().availableProcessors()
                    : -1.0)
            .withDescription("CPU load of the JVM process")
            .withFormat(MetricsConstants.FORMAT_FLOAT1);

    private static final FunctionGauge.Config<Long> OPEN_FDS_CONFIG = new FunctionGauge.Config<>(
            BENCHMARK_CATEGORY,
            "openFileDesc",
            Long.class,
            () -> osBean instanceof com.sun.management.UnixOperatingSystemMXBean unixBean
                    ? unixBean.getOpenFileDescriptorCount()
                    : -1L)
            .withDescription("Open file descriptors")
            .withFormat(MetricsConstants.FORMAT_INTEGER);


    public static void registerMetrics(PlatformContext context) {
        context.getMetrics().getOrCreate(TIMESTAMP_CONFIG);
        context.getMetrics().getOrCreate(MEM_TOT_CONFIG);
        context.getMetrics().getOrCreate(MEM_FREE_CONFIG);
        context.getMetrics().getOrCreate(DIRECT_MEM_CONFIG);
        context.getMetrics().getOrCreate(CPU_LOAD_PROC_CONFIG);
        context.getMetrics().getOrCreate(OPEN_FDS_CONFIG);

    }

    private static BufferPoolMXBean getDirectMemMXBean() {
        final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (final BufferPoolMXBean pool : pools) {
            if (pool.getName().equals("direct")) {
                return pool;
            }
        }
        return null;
    }
}
