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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * {@link OnSizeRolloverFileOutputStream} is an {@link OutputStream} implementation that supports rollover
 * size  functionality for the underlying file. Note: It's important to manage the lifecycle of the output
 * stream properly by calling {@link #flush()} and {@link #close()} methods when finished using it to ensure proper
 * resource cleanup.
 */
public class OnSizeRolloverFileOutputStream extends RolloverFileOutputStream {

    /**
     * Creates an {@link OnSizeRolloverFileOutputStream}. Supporting size based rollover.
     * Usage:
     * <pre>
     *     // Example usage with size-based rollover
     *     Path logPath = Paths.get("logs", "example.log");
     *     long maxFileSize = 1024 * 1024; // 1 MB
     *     boolean append = true;
     *     int maxRollover = 5; // Maximum number of rolling files
     *     OnSizeRolloverFileOutputStream outputStream = new OnSizeRolloverFileOutputStream(logPath, maxFileSize, append, maxRollover);
     * </pre>
     *
     * @param logPath         path where the logging file is located. Should contain the base dir + the name of the
     *                        logging file.
     * @param maxFileSize     maximum size for the file. The limit is checked with best effort
     * @param append          if true and the file exists, appends the content to the file. if not, the file is rolled.
     * @param maxRollover     Within a rolling period, how many rolling files are allowed.
     */
    public OnSizeRolloverFileOutputStream(
            @NonNull final Path logPath, final long maxFileSize, final boolean append, final int maxRollover) {
        super(logPath, maxFileSize, append, maxRollover);
        init(logPath);
    }

    @Override
    protected void roll() {
        doRoll(this::getPathFor);
    }

    private Path getPathFor(int index) {
        return logPath.resolve(baseName + "." + toPaddedDigitsString(index, indexLength) + dotExtension());
    }
}
