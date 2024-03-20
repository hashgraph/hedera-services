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

package com.swirlds.logging.io;

import static com.swirlds.logging.utils.StringUtils.toPaddedDigitsString;
import static java.time.ZoneOffset.UTC;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link OnSizeAndDateRolloverFileOutputStream} is an {@link OutputStream} implementation that supports rollover
 * size and date functionality for the underlying file. Note: It's important to manage the lifecycle of the output
 * stream properly by calling {@link #flush()} and {@link #close()} methods when finished using it to ensure proper
 * resource cleanup.
 */
public class OnSizeAndDateRolloverFileOutputStream extends RolloverFileOutputStream {
    private final DateTimeFormatter dateTimeFormatter;
    private final Supplier<Instant> instantSupplier;
    private Instant instant;

    /**
     * Creates an {@link OnSizeAndDateRolloverFileOutputStream}.
     * <p>*This constructor is meant for internal uses and test.
     *
     * @param logPath         path where the logging file is located. Should contain the base dir + the name of the
     *                        logging file.
     * @param maxFileSize     maximum size for the file. The limit is checked with best effort
     * @param append          if true and the file exists, appends the content to the file. if not, the file is rolled.
     * @param maxRollover     Within a rolling period, how many rolling files are allowed.
     * @param datePattern     Pattern used to name the file. E.G: yyyy-MM-dd
     * @param instantSupplier is used to get the current {@link Instant} instance. if not set the behaviour is defaulted
     *                        to {@link Instant#now()}
     */
    OnSizeAndDateRolloverFileOutputStream(
            @NonNull final Path logPath,
            final long maxFileSize,
            final boolean append,
            final int maxRollover,
            @NonNull final String datePattern,
            @NonNull final Supplier<Instant> instantSupplier) {
        super(logPath, maxFileSize, append, maxRollover);
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(datePattern).withZone(UTC);
        this.instantSupplier = instantSupplier;
        this.instant = instantSupplier.get();
        init(logPath);
    }

    /**
     * Creates an {@link OnSizeAndDateRolloverFileOutputStream}. Supporting size-and-date based rollover.
     * <p>
     * Usage:
     * <pre>
     *     Path logPath = Paths.get("logs", "example.log");
     *     long maxFileSize = 1024 * 1024; // 1 MB
     *     boolean append = true;
     *     int maxRollover = 5; // Maximum number of rolling files
     *     String datePattern = "yyyy-MM-dd"; // Date pattern for naming rolled files
     *     OnSizeAndDateRolloverFileOutputStreamImpl outputStream = new OnSizeAndDateRolloverFileOutputStreamImpl(logPath, maxFileSize, append, maxRollover, datePattern);
     * </pre>
     * <p>
     *
     * @param logPath     path where the logging file is located. Should contain the base dir + the name of the logging
     *                    file.
     * @param maxFileSize maximum size for the file. The limit is checked with best effort
     * @param append      if true and the file exists, appends the content to the file. if not, the file is rolled.
     * @param maxRollover Within a rolling period, how many rolling files are allowed.
     * @param datePattern Pattern used to name the file. E.G: yyyy-MM-dd
     */
    public OnSizeAndDateRolloverFileOutputStream(
            @NonNull final Path logPath,
            final long maxFileSize,
            final boolean append,
            final int maxRollover,
            @NonNull final String datePattern) {
        this(logPath, maxFileSize, append, maxRollover, datePattern, Instant::now);
    }

    @Override
    protected void roll() {
        final String formattedDate = dateTimeFormatter.format(instant);
        Function<Integer, Path> pathSupplier = i -> getPathFor(formattedDate, i);
        doRoll(pathSupplier);
        this.instant = instantSupplier.get();
    }

    protected int getNextIndex(final int currentIndex) {
        // This is simple enough but makes that the only way it works is with the pattern yyyy-MM-dd
        // It would be nice to support extra fields like hours of minutes.
        // FUTURE-WORK, guess if 1 unit of time happened for whatever pattern we receive.
        return Duration.between(instantSupplier.get(), instant).toDays() > 0 ? 0 : currentIndex;
    }

    private Path getPathFor(String date, int index) {
        return logPath.resolve(
                baseName + "-" + (date + "." + toPaddedDigitsString(index, indexLength)) + dotExtension());
    }
}
