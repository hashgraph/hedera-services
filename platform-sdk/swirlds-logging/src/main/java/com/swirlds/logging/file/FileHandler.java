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

package com.swirlds.logging.file;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Objects;

public class FileHandler extends AbstractSyncedHandler {

    private static final String FILE_NAME_PROPERTY = "%s.file";
    private static final String BUFFER_SIZE_PROPERTY = "%s.bufferSize";
    private static final String APPEND_PROPERTY = "%s.append";
    private static final String DEFAULT_FILE_NAME = "swirlds-log.log";

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    private final FileOutputStream fileOutputStream;
    private final OutputStreamWriter outputStreamWriter;
    private final BufferedWriter bufferedWriter;

    /**
     * Creates a new file handler.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public FileHandler(@NonNull String configKey, @NonNull Configuration configuration) {
        super(configKey, configuration);

        final String propertyPrefix = PROPERTY_HANDLER.formatted(configKey);
        final Path filePath = Objects.requireNonNullElse(
                configuration.getValue(FILE_NAME_PROPERTY.formatted(propertyPrefix), Path.class, null),
                Path.of(DEFAULT_FILE_NAME));
        final int bufferSize = Objects.requireNonNullElse(
                configuration.getValue(BUFFER_SIZE_PROPERTY.formatted(propertyPrefix), Integer.class, null), 8 * 1024);
        final boolean append = Objects.requireNonNullElse(
                configuration.getValue(APPEND_PROPERTY.formatted(propertyPrefix), Boolean.class, null), true);

        FileOutputStream fileOutputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        BufferedWriter bufferedWriter = null;
        try {
            fileOutputStream = new FileOutputStream(filePath.toFile(), append);
            outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            bufferedWriter = new BufferedWriter(outputStreamWriter, bufferSize);
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to create FileHandler", exception);
        }

        this.fileOutputStream = fileOutputStream;
        this.outputStreamWriter = outputStreamWriter;
        this.bufferedWriter = bufferedWriter;
    }

    /**
     * Handles a log event by appending it to the file using the {@link LineBasedFormat},
     * followed by flushing the console output.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull LogEvent event) {
        if (bufferedWriter != null) {
            LineBasedFormat.print(bufferedWriter, event);
        }
    }

    @Override
    protected void handleStopAndFinalize() {
        super.handleStopAndFinalize();
        try {
            if (bufferedWriter != null) {
                bufferedWriter.flush();
                bufferedWriter.close();
            }
            if (outputStreamWriter != null) {
                outputStreamWriter.close();
            }
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to close file output stream", exception);
        }
    }
}
