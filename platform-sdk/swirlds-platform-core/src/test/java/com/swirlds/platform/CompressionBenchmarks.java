package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;

import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.platform.gossip.sync.CompressedOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.airlift.compress.lz4.Lz4HadoopStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Checksum;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

class CompressionBenchmarks {

    private static final Path eventStreamPath = getAbsolutePath("~/Downloads/events");
    private static final int cycles = 1; // TODO use higher
    private static final int bufferSize = 4 * 1024 * 1024; // 1024*8 is the default size for the platform


    /**
     * Read the event stream data into a bunch of byte arrays. Do not combine into a single byte array (too much data to
     * fit into a single array).
     */
    @NonNull
    static List<byte[]> readData() throws IOException {

        System.out.println("Reading event stream data from " + eventStreamPath);

        final List<byte[]> fileBytes = new ArrayList<>();

        try (final Stream<Path> stream = Files.walk(eventStreamPath)) {
            stream.sorted().filter(Files::isRegularFile).forEachOrdered(path -> {
                // read file at path into byte array
                try {
                    final byte[] bytes = Files.readAllBytes(path);
                    fileBytes.add(bytes);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        // Merge individual byte arrays into one byte array
        final long totalSize = fileBytes.stream().mapToLong(bytes -> bytes.length).sum();

        System.out.println("Read " + new UnitFormatter(totalSize, UNIT_BYTES).render() + " from the event stream.");

        return fileBytes;
    }

    record TestResult(
            @NonNull String name,
            @NonNull Duration timeRequired,
            long totalUncompressedBytes,
            long totalCompressedBytes) {

        double getCompressionRatio() {
            return ((double) totalUncompressedBytes) / totalCompressedBytes;
        }
    }

    /**
     * Test the compression algorithm.
     *
     * @param name                the name of the compression algorithm
     * @param data                the data to compress
     * @param inputStreamBuilder  takes an input stream and wraps it in the compression input stream
     * @param outputStreamBuilder takes an output stream and wraps it in the compression output stream
     * @return the result of the test
     */
    @NonNull
    static TestResult testCompression(
            @NonNull final String name,
            @NonNull final List<byte[]> data,
            @NonNull final Function<InputStream, InputStream> inputStreamBuilder,
            @NonNull final Function<OutputStream, OutputStream> outputStreamBuilder)
            throws IOException, InterruptedException {

        System.out.println("Testing " + name);

        // Construct an input stream that is connected to the output stream
        final ServerSocket server = new ServerSocket(1234);
        final Socket clientSocket = new Socket("localhost", 1234);
        final Socket serverSocket = server.accept();

        final InputStream in = clientSocket.getInputStream();
        final OutputStream out = serverSocket.getOutputStream();

        final CountingStreamExtension inputCounter = new CountingStreamExtension();
        final InputStream meteredInputStream = new ExtendableInputStream(in, inputCounter);
        final CountingStreamExtension outputCounter = new CountingStreamExtension();
        final OutputStream meteredOutputStream = new ExtendableOutputStream(out, outputCounter);

        final InputStream inputStream = inputStreamBuilder.apply(meteredInputStream);
        final OutputStream outputStream = outputStreamBuilder.apply(meteredOutputStream);

        final AtomicBoolean finishedWriting = new AtomicBoolean(false);
        final CountDownLatch finishedReading = new CountDownLatch(1);

        final AtomicLong uncompressedBytesWritten = new AtomicLong(0);
        final AtomicLong uncompressedBytesRead = new AtomicLong(0);

        // Read and write on separate threads (so our buffers don't fill up)

        final Thread writeThread = new ThreadConfiguration(getStaticThreadManager())
                .setThreadName("writer")
                .setRunnable(() -> {
                    try {
                        long totalUncompressedBytesWritten = 0;

                        for (final byte[] datum : data) {
                            totalUncompressedBytesWritten += datum.length;
                            outputStream.write(datum);
                        }
                        outputStream.flush();
                        outputStream.close();
                        uncompressedBytesWritten.set(totalUncompressedBytesWritten);
                        finishedWriting.set(true);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).build();

        final Thread readThread = new ThreadConfiguration(getStaticThreadManager())
                .setThreadName("reader")
                .setRunnable(() -> {
                    try {
                        long totalUncompressedBytesRead = 0;

                        final byte[] buffer = new byte[1024 * 4];
                        while (true) {
                            final int count = inputStream.read(buffer);
                            if (count == -1) {
                                break;
                            }
                            totalUncompressedBytesRead += count;
//                            final long bytesWritten = uncompressedBytesWritten.get();
//                            if (bytesWritten > 0 && totalUncompressedBytesRead == bytesWritten) {
//                                break;
//                            }
                        }

                        uncompressedBytesRead.set(totalUncompressedBytesRead);
                        finishedReading.countDown();
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).build();

        final Instant start = Instant.now();

        writeThread.start();
        readThread.start();
        finishedReading.await();

        final Instant end = Instant.now();
        final Duration duration = Duration.between(start, end);

        // Sanity checks
        if (uncompressedBytesRead.get() != uncompressedBytesWritten.get()) {
            throw new IllegalStateException(
                    "totalUncompressedBytesRead != totalUncompressedBytesWritten, " + uncompressedBytesRead.get()
                            + " != "
                            + uncompressedBytesWritten.get());
        }

        final long totalCompressedBytesRead = inputCounter.getCount();
        final long totalCompressedBytesWritten = outputCounter.getCount();
        if (totalCompressedBytesRead != totalCompressedBytesWritten) {
            throw new IllegalStateException(
                    "totalCompressedBytesRead != totalCompressedBytesWritten, " + totalCompressedBytesRead + " != "
                            + totalCompressedBytesWritten);
        }

        serverSocket.close();
        clientSocket.close();
        server.close();

        final TestResult testResult = new TestResult(name, duration, uncompressedBytesRead.get(),
                totalCompressedBytesRead);
        System.out.println("      " + testResult);
        return testResult;
    }

    @NonNull
    static List<TestResult> computeAverageResults(@NonNull final List<TestResult> results) {
        final List<TestResult> averageResults = new ArrayList<>();

        final Set<String> uniqueTests = results.stream().map(result -> result.name).collect(Collectors.toSet());

        for (final String test : uniqueTests) {
            int count = 0;
            long totalTime = 0;
            long totalBytes = 0;
            long totalCompressedBytes = 0;
            for (final TestResult result : results) {
                if (result.name.equals(test)) {
                    count++;
                    totalTime += result.timeRequired.toNanos();
                    totalBytes += result.totalUncompressedBytes;
                    totalCompressedBytes += result.totalCompressedBytes;
                }
            }
            final Duration averageTime = Duration.ofNanos(totalTime / count);
            // Output should probably be deterministic, but it's simpler here to assume it's not
            final long averageBytes = totalBytes / count;
            final long averageCompressedBytes = totalCompressedBytes / count;
            averageResults.add(new TestResult(test, averageTime, averageBytes, averageCompressedBytes));
        }

        return averageResults;
    }

    @NonNull
    static TestResult testNoCompression(@NonNull final List<byte[]> data) throws IOException, InterruptedException {
        return testCompression("No Compression", data,
                in -> new BufferedInputStream(in, bufferSize),
                out -> new BufferedOutputStream(out, bufferSize));

    }

    @NonNull
    static TestResult testGzipCompression(@NonNull final List<byte[]> data) throws IOException, InterruptedException {
        return testCompression("gzip", data,
                in -> new InflaterInputStream(in, new Inflater(true), bufferSize),
                out -> {
                    final int level = Deflater.DEFAULT_COMPRESSION;
                    return new DeflaterOutputStream(out, new Deflater(level, true), bufferSize, true);
                });
    }

    @NonNull
    static TestResult testLz4JavaCompression(@NonNull final List<byte[]> data)
            throws IOException, InterruptedException {
        return testCompression("lz4-java", data,
                in -> new LZ4BlockInputStream(in),
                out -> {
                    final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();

                    final Checksum checksum = XXHashFactory.fastestInstance()
                            .newStreamingHash32(0x9747b28c)
                            .asChecksum();

                    return new LZ4BlockOutputStream(out, bufferSize, compressor, checksum, true);
                });
    }

    static TestResult testAircompressorCompression(@NonNull final List<byte[]> data)
            throws IOException, InterruptedException {
        return testCompression("aircompressor (lz4)", data,
                in ->  new Lz4HadoopStreams(bufferSize).createInputStream(in),
                out -> new CompressedOutputStream(out, bufferSize));
    }

    static void runBenchmark() throws IOException, InterruptedException {
        final List<byte[]> data = readData();

        final List<TestResult> results = new ArrayList<>();

        for (int i = 0; i < cycles; i++) {
            results.add(testNoCompression(data));
            //results.add(testGzipCompression(data));
            results.add(testAircompressorCompression(data));
            results.add(testLz4JavaCompression(data));
        }

        final List<TestResult> averageResults = computeAverageResults(results);

        final TextTable table = new TextTable();
        table.addRow("Algorithm", "Time", "Compression Ratio");

        for (final TestResult result : averageResults) {
            table.addRow(
                    result.name,
                    new UnitFormatter(result.timeRequired().toNanos(), UNIT_NANOSECONDS).render(),
                    result.getCompressionRatio());
        }

        System.out.println(table.render());


    }

    public static void main(@NonNull final String[] args) {
        try {
            runBenchmark();
        } catch (final Throwable e) {
            e.printStackTrace();
        }
    }
}
