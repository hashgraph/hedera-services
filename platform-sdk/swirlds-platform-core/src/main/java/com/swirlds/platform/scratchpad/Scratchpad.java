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

package com.swirlds.platform.scratchpad;

import static com.swirlds.common.io.utility.TemporaryFileBuilder.buildTemporaryFile;
import static com.swirlds.logging.LogMarker.STARTUP;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO unit test

/**
 * A utility that allows the platform to "take notes" that are preserved across restart boundaries.
 * <p>
 * The scratch pad is thread safe. All read operations and write operations are atomic. Any write that has completed
 * is guaranteed to be visible to all subsequent reads, regardless of crashes/restarts.
 */
public class Scratchpad {

    private static final Logger logger = LogManager.getLogger(Scratchpad.class);

    /**
     * The directory where scratchpad files are written. Files are written with the format "0.scr", "1.scr", etc., with
     * the number increasing by one each time a file is written. If multiple files are present, the file with the
     * highest number is the most recent and is always used. Multiple files will only be present if the platform crashes
     * after writing a file but before it has the opportunity to delete the previous file.
     */
    public static final String SCRATCHPAD_DIRECTORY_NAME = "scratchpad";

    /**
     * The file extension used for scratchpad files.
     */
    public static final String SCRATCHPAD_FILE_EXTENSION = ".scr";

    private static final Map<Integer, ScratchpadField> indexToFieldMap = new HashMap<>();

    static {
        for (final ScratchpadField field : ScratchpadField.values()) {
            final ScratchpadField previous = indexToFieldMap.put(field.getIndex(), field);
            if (previous != null) {
                throw new RuntimeException("duplicate scratchpad field index " + field.getIndex());
            }
        }
    }

    private final Map<ScratchpadField, SelfSerializable> data = new HashMap<>();
    private final AutoClosableLock lock = Locks.createAutoLock();
    private final Path scratchpadDirectory;
    private long nextScratchpadIndex;

    private final int fileVersion = 1;

    public Scratchpad(@NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        scratchpadDirectory = stateConfig
                .savedStateDirectory()
                .resolve(SCRATCHPAD_DIRECTORY_NAME)
                .resolve(Long.toString(selfId.id()));

        loadFromDisk();
        logContents();
    }

    /**
     * Get a value from the scratchpad.
     *
     * @param field the field to get
     * @param <T>   the type of the value
     * @return the value, or null if the field is not present
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T extends SelfSerializable> T get(final ScratchpadField field) {
        try (final Locked ignored = lock.lock()) {
            return (T) data.get(field);
        }
    }

    /**
     * Set a field in the scratchpad. The scratchpad file is updated atomically. When this method returns, the data
     * written to the scratchpad will be present the next time the scratchpad is checked, even if that is after a
     * restart boundary.
     *
     * @param field the field to set
     * @param value the value to set, may be null
     * @param <T>   the type of the value
     */
    public <T extends SelfSerializable> void set(@NonNull final ScratchpadField field, @Nullable final T value) {
        logger.info(STARTUP.getMarker(), "Setting scratchpad field {} to {}", field, value);
        try (final Locked ignored = lock.lock()) {
            data.put(field, value);
            flush();
        }
    }

    /**
     * Log the contents of the scratchpad.
     */
    public void logContents() {
        final TextTable table = new TextTable().setBordersEnabled(false);

        for (final ScratchpadField field : ScratchpadField.values()) {
            final SelfSerializable value = data.get(field);
            if (value == null) {
                table.addToRow(field.name(), "null");
            } else {
                table.addRow(field.name(), value.toString());
            }
        }

        logger.info(
                STARTUP.getMarker(),
                """
                        Scratchpad contents:
                        {}""",
                table.render());
    }

    /**
     * Parse the scratchpad file from disk.
     */
    private void loadFromDisk() {
        try {
            final List<Path> files = getScratchpadFiles();
            if (files.isEmpty()) {
                return;
            }

            // Delete all files except for the most recent one
            for (int index = 0; index < files.size() - 1; index++) {
                Files.delete(files.get(index));
            }

            final Path scratchpadFile = files.get(files.size() - 1);
            nextScratchpadIndex = getFileIndex(scratchpadFile) + 1;

            final SerializableDataInputStream in = new SerializableDataInputStream(
                    new BufferedInputStream(new FileInputStream(scratchpadFile.toFile())));

            final int fileVersion = in.readInt();
            if (fileVersion != this.fileVersion) {
                throw new RuntimeException("scratchpad file version mismatch");
            }

            int fieldCount = in.readInt();

            for (int index = 0; index < fieldCount; index++) {
                final int fieldId = in.readInt();
                final ScratchpadField field = indexToFieldMap.get(fieldId);
                if (field == null) {
                    throw new IOException("scratchpad file contains unknown field " + fieldId);
                }

                final SelfSerializable value = in.readSerializable();
                data.put(field, value);
            }

        } catch (final IOException e) {
            throw new RuntimeException("unable to load scratchpad", e);
        }
    }

    /**
     * Write the data in the scratchpad to a temporary file.
     *
     * @return the path to the temporary file that was written
     */
    @NonNull
    private Path flushToTemporaryFile() throws IOException {
        final Path temporaryFile = buildTemporaryFile();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temporaryFile.toFile())));

        out.writeInt(fileVersion);

        int fieldCount = 0;
        for (final ScratchpadField field : ScratchpadField.values()) {
            if (data.get(field) != null) {
                fieldCount++;
            }
        }
        out.writeInt(fieldCount);

        for (final ScratchpadField field : ScratchpadField.values()) {
            final SelfSerializable value = data.get(field);
            if (value != null) {
                out.writeInt(field.getIndex());
                out.writeSerializable(value, true);
            }
        }
        out.close();

        return temporaryFile;
    }

    /**
     * Generate the path to the next scratchpad file.
     *
     * @return the path to the next scratchpad file
     */
    @NonNull
    private Path generateNextFilePath() {
        return scratchpadDirectory.resolve((nextScratchpadIndex++) + SCRATCHPAD_FILE_EXTENSION);
    }

    /**
     * Get the file index of a scratchpad file.
     *
     * @param path the path to the scratchpad file
     * @return the file index
     */
    private long getFileIndex(@NonNull final Path path) {
        final String fileName = path.getFileName().toString();
        return Long.parseLong(fileName.substring(0, fileName.indexOf('.')));
    }

    /**
     * Get a list of all scratchpad files currently on disk sorted from lowest index to highest index.
     */
    @NonNull
    private List<Path> getScratchpadFiles() {
        if (!Files.exists(scratchpadDirectory)) {
            return List.of();
        }

        final List<Path> files = new ArrayList<>();

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(scratchpadDirectory)) {
            for (final var path : stream) {
                if (path.toString().endsWith(SCRATCHPAD_FILE_EXTENSION)) {
                    files.add(path);
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException("unable to list scratchpad files", e);
        }

        files.sort((a, b) -> Long.compare(getFileIndex(a), getFileIndex(b)));

        return files;
    }

    /**
     * Flush the scratchpad to disk atomically. Blocks until complete.
     */
    private void flush() {
        try {
            final List<Path> scratchpadFiles = getScratchpadFiles();

            final Path temporaryFile = flushToTemporaryFile();

            if (!Files.exists(scratchpadDirectory)) {
                Files.createDirectories(scratchpadDirectory);
            }
            Files.move(temporaryFile, generateNextFilePath(), ATOMIC_MOVE);

            for (final Path scratchpadFile : scratchpadFiles) {
                Files.delete(scratchpadFile);
            }
        } catch (final IOException e) {
            throw new RuntimeException("unable to flush scratchpad", e);
        }
    }

    // FUTURE WORK:
    //  - atomic reads and writes of multiple fields
    //  - pcli command to look inside a scratchpad file
    //  - pcli command to edit a scratchpad file
}
