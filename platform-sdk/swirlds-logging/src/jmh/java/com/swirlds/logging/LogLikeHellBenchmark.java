package com.swirlds.logging;

import static com.swirlds.logging.BenchmarkConstants.FORK_COUNT;
import static com.swirlds.logging.BenchmarkConstants.MEASUREMENT_ITERATIONS;
import static com.swirlds.logging.BenchmarkConstants.MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION;
import static com.swirlds.logging.BenchmarkConstants.PARALLEL_THREAD_COUNT;
import static com.swirlds.logging.BenchmarkConstants.WARMUP_ITERATIONS;
import static com.swirlds.logging.BenchmarkConstants.WARMUP_TIME_IN_SECONDS_PER_ITERATION;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.console.ConsoleHandlerFactory;
import com.swirlds.logging.file.FileHandlerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class LogLikeHellBenchmark {
    

    @Param({"FILE", "CONSOLE", "FILE_AND_CONSOLE"})
    public String loggingType;

    Logger logger;
    LoggingSystem loggingSystem;
    LogLikeHell logLikeHell;

    @Setup(org.openjdk.jmh.annotations.Level.Iteration)
    public void init() throws Exception {
        Files.deleteIfExists(Path.of("target/log-like-hell-benchmark.log"));
        Files.deleteIfExists(Path.of("log-like-hell-benchmark.log"));

        loggingSystem = null;
        if (Objects.equals(loggingType, "FILE")) {
            configureFileLogging();
        } else if (Objects.equals(loggingType, "CONSOLE")) {
            configureConsoleLogging();
        } else if (Objects.equals(loggingType, "FILE_AND_CONSOLE")) {
            configureFileAndConsoleLogging();
        }

        if (loggingSystem != null) {
            logger = loggingSystem.getLogger(LogLikeHellBenchmark.class.getSimpleName());
            logLikeHell = new LogLikeHell(logger);
        } else {
            throw new IllegalStateException("Invalid logging type: " + loggingType);
        }

    }

    private void configureFileLogging() {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.active", "true")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", "benchmark.log")
                .build();
        final LogHandler fileHandler = new FileHandlerFactory().create("file", configuration);
        loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(fileHandler);
    }

    private void configureConsoleLogging() {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.active", "true")
                .withValue("logging.handler.console.level", "trace")
                .build();
        final LogHandler consoleHandler = new ConsoleHandlerFactory().create("console", configuration);
        loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(consoleHandler);
    }

    private void configureFileAndConsoleLogging() {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.active", "true")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", "benchmark.log")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.active", "true")
                .withValue("logging.handler.console.level", "trace")
                .build();
        final LogHandler fileHandler = new FileHandlerFactory().create("file", configuration);
        final LogHandler consoleHandler = new ConsoleHandlerFactory().create("console", configuration);
        loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(fileHandler);
        loggingSystem.addHandler(consoleHandler);
    }


    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runLogLikeHell() {
        logLikeHell.run();
    }


    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runSingleSimpleLog() {
        logger.log(Level.INFO, "Hello World");
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runSingleComplexLog() {
        logger.withMarker("MARKER1").
                withMarker("MARKER2").
                withMarker("MARKER3").
                withContext("user", "user1").
                withContext("transaction", "t34").
                withContext("session", "session56").
                log(Level.INFO, "Hello {}", new RuntimeException("OHOH"), "World");
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runLoggerInstantiation(Blackhole blackhole) {
        final Logger logger1 = loggingSystem.getLogger(LogLikeHellBenchmark.class.getSimpleName());
        blackhole.consume(logger1);
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runLoggerInstantiationAndSingleSimpleLog() {
        final Logger logger1 = loggingSystem.getLogger(LogLikeHellBenchmark.class.getSimpleName());
        logger1.log(Level.INFO, "Hello World");
    }

}
