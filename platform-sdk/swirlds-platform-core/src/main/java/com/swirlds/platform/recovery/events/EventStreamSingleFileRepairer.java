/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.events;

import static com.swirlds.common.crypto.DigestType.SHA_384;
import static com.swirlds.common.stream.internal.TimestampStreamFileWriter.OBJECT_STREAM_VERSION;

import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.EventStreamType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Repair a single event stream file if it is damaged or missing a final running hash.  While evaluating the file for
 * repair, a new temporary file is created with the same name followed by the ".repaired" suffix. If the file is damaged
 * and repaired, the original file is backed up to a file with the same name followed by the ".damaged" suffix and the
 * original file is replaced with the repaired file. If the original file is not repaired, no change is made.   The
 * repaired file is always created.  If everything executes properly, the repaired file is deleted at the end of the
 * script.
 */
public class EventStreamSingleFileRepairer {

    /**
     * The suffix to apply to temporary repaired event stream files.
     */
    public static final String REPAIRED_SUFFIX = ".repaired";
    /**
     * The suffix to apply to backups of damaged event stream files.
     */
    public static final String DAMAGED_SUFFIX = ".damaged";
    /**
     * The original event stream file.
     */
    private final File originalFile;
    /**
     * The backup file for the damaged original file, if it is repaired.
     */
    private final File damagedFile;
    /**
     * A temporary and possibly repaired event stream file, created while evaluating the original file.
     */
    private final File repairedFile;
    /**
     * The input stream of the original file, stored to ensure we close it before moving files around.
     */
    private final InputStream in;
    /**
     * The repairing iterator for the event stream.
     */
    private final EventStreamSingleFileRepairIterator repairIterator;

    public EventStreamSingleFileRepairer(final File file) throws IOException {
        this.originalFile = file;

        repairedFile = new File(originalFile.getAbsolutePath() + REPAIRED_SUFFIX);
        if (repairedFile.exists() && !Files.deleteIfExists(repairedFile.toPath())) {
            throw new IOException("Not able to delete an old repaired file: " + repairedFile.getAbsolutePath());
        }

        damagedFile = new File(originalFile.getAbsolutePath() + DAMAGED_SUFFIX);

        // Create repairing iterator
        in = new BufferedInputStream(new FileInputStream(originalFile));
        repairIterator = new EventStreamSingleFileRepairIterator(new ObjectStreamIterator<>(in, true));
    }

    /**
     * Iterates through the input file and writes the possibly repaired event stream to the repaired file name.
     *
     * @return true if the written event stream was repaired, false otherwise.
     * @throws IOException              if a critical exception occurred while traversing the event stream.
     * @throws NoSuchAlgorithmException if the cryptography is not accessible.
     */
    private boolean repairFile() throws IOException, NoSuchAlgorithmException {
        try (final SerializableDataOutputStream out =
                new SerializableDataOutputStream(new BufferedOutputStream(new HashingOutputStream(
                        MessageDigest.getInstance(SHA_384.algorithmName()),
                        new FileOutputStream(repairedFile, false))))) {
            for (final int item : EventStreamType.getInstance().getFileHeader()) {
                out.writeInt(item);
            }
            out.writeInt(OBJECT_STREAM_VERSION);
            while (repairIterator.hasNext()) {
                out.writeSerializable(repairIterator.next(), true);
            }
        }
        return repairIterator.finalHashAdded();
    }

    /**
     * Gets the count of the number of DetailedConsensusEvents encountered by the repair iterator.
     *
     * @return the number of DetailedConsensusEvents.
     */
    public int getEventCount() {
        return repairIterator.getEventCount();
    }

    /**
     * Attempts to repair the event stream file. If the file is repaired, the original file is backed up with a
     * ".damaged" suffix and replaced with the repaired file. If the original file was not repaired, no change is made.
     *
     * @return true if the original event stream file was repaired, false otherwise.
     * @throws IOException              if a critical exception occurred while traversing the event stream.
     * @throws NoSuchAlgorithmException if the cryptography is not accessible.
     */
    public boolean repair() throws IOException, NoSuchAlgorithmException {
        final boolean repaired = repairFile();

        try {
            in.close();
        } catch (IOException e) {
            // may have already been closed
        }

        if (repaired) {
            Files.copy(originalFile.toPath(), damagedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(repairedFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        // this file is always created and should be deleted in all cases
        if (repairedFile.exists() && !repairedFile.delete()) {
            throw new IOException("Not able to delete the repaired file: " + repairedFile.getAbsolutePath());
        }

        return repaired;
    }
}
