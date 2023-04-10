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

package com.swirlds.benchmark;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.swirlds.benchmark.config.BenchmarkConfig;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BenchmarkMetrics {

    private static final Logger logger = LogManager.getLogger(BenchmarkMetrics.class);

    private static final String BENCHMARK_CATEGORY = "BENCHMARK";
    private static final String FORMAT_INTEGER = " %d";
    private static final String FORMAT_FLOAT0 = " %.0f";
    private static final String FORMAT_FLOAT1 = " %.1f";

    private static final BenchmarkMetrics INSTANCE = new BenchmarkMetrics();

    // Configurable parameters
    private static String csvOutputFolder;
    private static String csvFileName;
    private static boolean csvAppend;
    private static int csvWriteFrequency;
    private static String deviceName;

    private Path csvFilePath;
    private ScheduledExecutorService metricService;
    private String origMetricString;
    private String curMetricString;
    private Metrics metrics;

    /*
     *    System metrics: time, memory, CPU
     */

    private static final FunctionGauge.Config<String> TIMESTAMP_CONFIG = new FunctionGauge.Config<>(
                    "AAA", "time", String.class, () -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                            .format(Instant.now().atZone(ZoneId.of("UTC"))))
            .withDescription("the current time")
            .withFormat("%24s");

    private static final FunctionGauge.Config<Long> MEM_TOT_CONFIG = new FunctionGauge.Config<>(
                    "AAB", "memTot", Long.class, Runtime.getRuntime()::totalMemory)
            .withDescription("total bytes in the JVM heap")
            .withFormat(FORMAT_INTEGER);

    private static final FunctionGauge.Config<Long> MEM_FREE_CONFIG = new FunctionGauge.Config<>(
                    "AAC", "memFree", Long.class, Runtime.getRuntime()::freeMemory)
            .withDescription("free bytes in the JVM heap")
            .withFormat(FORMAT_INTEGER);

    private static final BufferPoolMXBean directMemMXBean = getDirectMemMXBean();

    private static final FunctionGauge.Config<Long> DIRECT_MEM_CONFIG = new FunctionGauge.Config<>(
                    "AAD",
                    "directMem",
                    Long.class,
                    directMemMXBean != null ? directMemMXBean::getMemoryUsed : () -> -1L)
            .withDescription("used bytes of the JVM direct memory")
            .withFormat(FORMAT_INTEGER);

    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    private static final FunctionGauge.Config<Double> CPU_LOAD_PROC_CONFIG = new FunctionGauge.Config<>(
                    "AAE",
                    "cpuLoadProc",
                    Double.class,
                    () -> osBean instanceof com.sun.management.OperatingSystemMXBean sunBean
                            ? sunBean.getProcessCpuLoad() * Runtime.getRuntime().availableProcessors()
                            : -1.0)
            .withDescription("CPU load of the JVM process")
            .withFormat(FORMAT_FLOAT1);

    private static final FunctionGauge.Config<Long> OPEN_FDS_CONFIG = new FunctionGauge.Config<>(
                    "AAF",
                    "openFileDesc",
                    Long.class,
                    () -> osBean instanceof com.sun.management.UnixOperatingSystemMXBean unixBean
                            ? unixBean.getOpenFileDescriptorCount()
                            : -1L)
            .withDescription("Open file descriptors")
            .withFormat(FORMAT_INTEGER);

    private static final RunningAverageMetric.Config TPS_CONFIG = new RunningAverageMetric.Config(
                    BENCHMARK_CATEGORY, "tps")
            .withDescription("transactions per second")
            .withFormat(FORMAT_FLOAT0);

    private BenchmarkMetrics() {
        // prevent instantiation
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

    /*
     *    Disk I/O metrics (Linux only)
     */

    private static final int DISK_STAT_ROPS = 0;
    private static final int DISK_STAT_RSEC = 2;
    private static final int DISK_STAT_RTIME = 3;
    private static final int DISK_STAT_WOPS = 4;
    private static final int DISK_STAT_WSEC = 6;
    private static final int DISK_STAT_WTIME = 7;

    private int sectorSize;
    private boolean diskMetricsRegistered;
    private long prevTime;
    private long curTime;
    private long[] prevDiskStats;
    private long[] curDiskStats;

    private Path getDiskSectorSizeFile() {
        return Path.of("/sys/block").resolve(deviceName).resolve("queue/hw_sector_size");
    }

    private Path getDiskStatsFile() {
        return Path.of("/sys/block").resolve(deviceName).resolve("stat");
    }

    private final FunctionGauge.Config<Double> diskReadOpsConfig = new FunctionGauge.Config<>(
                    "ADA",
                    "diskReadOps/s",
                    Double.class,
                    () -> 1000. * (curDiskStats[DISK_STAT_ROPS] - prevDiskStats[DISK_STAT_ROPS]) / (curTime - prevTime))
            .withDescription("Disk read operations per sec")
            .withFormat(FORMAT_FLOAT0);

    private final FunctionGauge.Config<Double> diskReadBytesConfig = new FunctionGauge.Config<>(
                    "ADB",
                    "diskReadBytes/s",
                    Double.class,
                    () -> 1000.
                            * sectorSize
                            * (curDiskStats[DISK_STAT_RSEC] - prevDiskStats[DISK_STAT_RSEC])
                            / (curTime - prevTime))
            .withDescription("Disk read bytes per sec")
            .withFormat(FORMAT_FLOAT0);

    private final FunctionGauge.Config<Long> diskReadTimeConfig = new FunctionGauge.Config<>(
                    "ADC",
                    "diskReadTime",
                    Long.class,
                    () -> (curDiskStats[DISK_STAT_RTIME] - prevDiskStats[DISK_STAT_RTIME]))
            .withDescription("Disk read ops time (ms)")
            .withFormat(FORMAT_INTEGER);

    private final FunctionGauge.Config<Double> diskWriteOpsConfig = new FunctionGauge.Config<>(
                    "ADD",
                    "diskWriteOps/s",
                    Double.class,
                    () -> 1000. * (curDiskStats[DISK_STAT_WOPS] - prevDiskStats[DISK_STAT_WOPS]) / (curTime - prevTime))
            .withDescription("Disk write operations per sec")
            .withFormat(FORMAT_FLOAT0);

    private final FunctionGauge.Config<Double> diskWriteBytesConfig = new FunctionGauge.Config<>(
                    "ADE",
                    "diskWriteBytes/s",
                    Double.class,
                    () -> 1000.
                            * sectorSize
                            * (curDiskStats[DISK_STAT_WSEC] - prevDiskStats[DISK_STAT_WSEC])
                            / (curTime - prevTime))
            .withDescription("Disk write bytes per sec")
            .withFormat(FORMAT_FLOAT0);

    private final FunctionGauge.Config<Long> diskWriteTimeConfig = new FunctionGauge.Config<>(
                    "ADF",
                    "diskWriteTime",
                    Long.class,
                    () -> (curDiskStats[DISK_STAT_WTIME] - prevDiskStats[DISK_STAT_WTIME]))
            .withDescription("Disk write ops time (ms)")
            .withFormat(FORMAT_INTEGER);

    private void updateDiskMetrics() {
        if (!diskMetricsRegistered) {
            return;
        }
        Path diskStatsFile = getDiskStatsFile();
        try {
            prevTime = curTime;
            curTime = System.currentTimeMillis();
            prevDiskStats = curDiskStats;
            String[] strs = Files.readString(diskStatsFile).trim().split("\\s+");
            curDiskStats = Arrays.stream(strs).mapToLong(Long::parseLong).toArray();
        } catch (IOException | NumberFormatException ex) {
            logger.error("Can't read disk metrics from {}: {} ", diskStatsFile, ex);
            diskMetricsRegistered = false;
        }
    }

    private void registerDiskMetrics() {
        Path diskSectorSizeFile = getDiskSectorSizeFile();
        if (!Files.exists(diskSectorSizeFile)) {
            return;
        }
        try {
            String str = Files.readString(diskSectorSizeFile).trim();
            sectorSize = Integer.parseInt(str);
        } catch (IOException | NumberFormatException ex) {
            logger.error("Can't parse {}: {} ", diskSectorSizeFile, ex);
            return;
        }
        metrics.getOrCreate(diskReadOpsConfig);
        metrics.getOrCreate(diskReadBytesConfig);
        metrics.getOrCreate(diskReadTimeConfig);
        metrics.getOrCreate(diskWriteOpsConfig);
        metrics.getOrCreate(diskWriteBytesConfig);
        metrics.getOrCreate(diskWriteTimeConfig);
        diskMetricsRegistered = true;

        updateDiskMetrics();
    }

    /*
     *    General
     */

    private static String printableName(final Metric metric, final Metric.ValueType valueType) {
        String printableName = metric.getName();
        if (valueType != Metric.ValueType.VALUE) {
            printableName += "." + valueType;
        }
        return printableName;
    }

    private static String getMetricNames(final Collection<Metric> collection) {
        return collection.stream()
                .flatMap(metric -> metric.getValueTypes().stream().map(valueType -> printableName(metric, valueType)))
                .collect(Collectors.joining(",", "", System.lineSeparator()));
    }

    private static String getMetricValues(final Collection<Metric> collection) {
        return collection.stream()
                .flatMap(metric -> metric.getValueTypes().stream()
                        .map(valueType -> String.format(metric.getFormat().replace(",", ""), metric.get(valueType))))
                .collect(Collectors.joining(",", "", System.lineSeparator()));
    }

    private void reportMetrics() {
        try {
            if (csvFilePath == null) {
                String folderName = csvOutputFolder;
                if (folderName.isBlank()) {
                    csvFilePath = BaseBench.getBenchDir().resolve(csvFileName);
                } else {
                    csvFilePath = Path.of(folderName).resolve(csvFileName);
                }
                if (csvAppend && Files.exists(csvFilePath)) {
                    Files.writeString(csvFilePath, "\n\n", APPEND);
                } else {
                    Files.writeString(csvFilePath, "", CREATE, WRITE, TRUNCATE_EXISTING);
                }
            }

            // Update metrics
            updateDiskMetrics();

            Collection<Metric> allMetrics = metrics.getAll();
            String names = getMetricNames(allMetrics);
            if (origMetricString.equals(names)) {
                return;
            }
            if (!curMetricString.equals(names)) {
                curMetricString = names;
                Files.writeString(csvFilePath, names, APPEND);
            }
            String values = getMetricValues(allMetrics);
            Files.writeString(csvFilePath, values, APPEND);
        } catch (IOException ioex) {
            logger.error("Can't write to {}: {}", csvFilePath, ioex);
        }
    }

    private void setupInstance() {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(MetricsConfig.class)
                .build();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final MetricKeyRegistry registry = new MetricKeyRegistry();
        metricService = getStaticThreadManager().createSingleThreadScheduledExecutor("benchmark: MetricsWriter");
        metrics = new DefaultMetrics(null, registry, metricService, new DefaultMetricsFactory(), metricsConfig);

        metrics.getOrCreate(TIMESTAMP_CONFIG);
        metrics.getOrCreate(MEM_TOT_CONFIG);
        metrics.getOrCreate(MEM_FREE_CONFIG);
        metrics.getOrCreate(DIRECT_MEM_CONFIG);
        metrics.getOrCreate(CPU_LOAD_PROC_CONFIG);
        metrics.getOrCreate(OPEN_FDS_CONFIG);
        registerDiskMetrics();

        origMetricString = curMetricString = getMetricNames(metrics.getAll());
        if (csvWriteFrequency > 0) {
            metricService.scheduleAtFixedRate(
                    this::reportMetrics, csvWriteFrequency, csvWriteFrequency, TimeUnit.MILLISECONDS);
        }
    }

    public static void configure(BenchmarkConfig config) {
        csvOutputFolder = config.csvOutputFolder();
        csvFileName = config.csvFileName();
        csvAppend = config.csvAppend();
        csvWriteFrequency = config.csvWriteFrequency();
        deviceName = config.deviceName();
    }

    public static void start() {
        INSTANCE.setupInstance();
    }

    public static void start(BenchmarkConfig config) {
        configure(config);
        start();
    }

    public static void register(final Consumer<Metrics> consumer) {
        consumer.accept(INSTANCE.metrics);
    }

    public static RunningAverageMetric registerTPS() {
        return INSTANCE.metrics.getOrCreate(TPS_CONFIG);
    }

    public static void report() {
        INSTANCE.reportMetrics();
    }

    public static void reset() {
        INSTANCE.metrics.resetAll();
        INSTANCE.csvFilePath = null;
    }

    public static void stop() {
        INSTANCE.metricService.shutdownNow();
    }
}
