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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.internal.EventImpl;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Represents a pre-consensus event file that can be written to.
 */
public class PreConsensusEventMutableFile {

    /**
     * Describes the file that is being written to.
     */
    private final PreConsensusEventFile descriptor;

    /**
     * Counts the bytes written to the file.
     */
    private final CountingStreamExtension counter;

    /**
     * The highest generation of all events written to the file.
     */
    private long highestGenerationInFile = Long.MIN_VALUE;

    /**
     * The output stream to write to.
     */
    private final SerializableDataOutputStream out;

    /**
     * Create a new pre-consensus event file that can be written to.
     *
     * @param descriptor
     * 		a description of the file
     */
    PreConsensusEventMutableFile(final PreConsensusEventFile descriptor) throws IOException {
        if (Files.exists(descriptor.path())) {
            throw new IOException("File " + descriptor.path() + " already exists");
        }

        Files.createDirectories(descriptor.path().getParent());

        this.descriptor = descriptor;
        counter = new CountingStreamExtension(false);
        out = new SerializableDataOutputStream(new ExtendableOutputStream(
                new BufferedOutputStream(new FileOutputStream(descriptor.path().toFile())), counter));
    }

    /**
     * Check if this file is eligible to contain an event based on generational bounds.
     *
     * @param event
     * 		the event in question
     * @return true if this file is eligible to contain the event
     */
    public boolean canContain(final EventImpl event) {
        return descriptor.canContain(event);
    }

    /**
     * Write an event to the file.
     *
     * @param event
     * 		the event to write
     */
    public void writeEvent(final EventImpl event) throws IOException {
        if (!descriptor.canContain(event)) {
            throw new IllegalStateException("Cannot write event " + event.getBaseHash() + " with generation "
                    + event.getGeneration() + " to file " + descriptor);
        }
        out.writeSerializable(event, false);
        highestGenerationInFile = Math.max(highestGenerationInFile, event.getGeneration());
    }

    /**
     * Flush the file.
     */
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Close the file.
     */
    public void close() throws IOException {
        out.close();
    }

    /**
     * Get the current size of the file, in bytes.
     *
     * @return the size of the file in bytes
     */
    public long fileSize() {
        return counter.getCount();
    }

    /**
     * Get the difference between the highest generation written to the
     * file and the lowest legal generation for this file. Higher values
     * mean that the maximum generation was chosen well.
     */
    public long getUtilizedGenerationalSpan() {
        if (highestGenerationInFile == Long.MIN_VALUE) {
            return 0;
        }

        return highestGenerationInFile - descriptor.minimumGeneration();
    }

    /**
     * Get the generational span that is unused in this file. Low values
     * mean that the maximum generation was chosen well, resulting
     * in less overlap between files. A value of 0 represents a
     * "perfect" choice.
     */
    public long getUnUtilizedGenerationalSpan() {
        return descriptor.maximumGeneration() - highestGenerationInFile;
    }

    /**
     * Get the span of generations that this file can legally contain.
     */
    public long getGenerationalSpan() {
        return descriptor.maximumGeneration() - descriptor.minimumGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return descriptor.toString();
    }
}
