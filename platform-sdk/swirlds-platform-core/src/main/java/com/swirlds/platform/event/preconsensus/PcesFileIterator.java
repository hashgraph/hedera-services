/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.platform.event.GossipEvent;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.EventSerializationUtils;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Iterates over the events in a single preconsensus event file.
 */
public class PcesFileIterator implements IOIterator<PlatformEvent> {

    private final long lowerBound;
    private final AncientMode fileType;
    private final SerializableDataInputStream stream;
    private boolean hasPartialEvent = false;
    private final CountingStreamExtension counter;
    private PlatformEvent next;
    private boolean streamClosed = false;
    private PcesFileVersion fileVersion;

    /**
     * Create a new iterator that walks over events in a preconsensus event file.
     *
     * @param fileDescriptor describes a preconsensus event file
     * @param lowerBound     the lower bound for all events to be returned, corresponds to either generation or birth
     *                       round depending on the {@link PcesFile} type
     * @param fileType       the type of file to read
     */
    public PcesFileIterator(
            @NonNull final PcesFile fileDescriptor, final long lowerBound, @NonNull final AncientMode fileType)
            throws IOException {

        this.lowerBound = lowerBound;
        this.fileType = Objects.requireNonNull(fileType);
        counter = new CountingStreamExtension();
        stream = new SerializableDataInputStream(new ExtendableInputStream(
                new BufferedInputStream(
                        new FileInputStream(fileDescriptor.getPath().toFile())),
                counter));

        try {
            final int fileVersionNumber = stream.readInt();
            fileVersion = PcesFileVersion.fromVersionNumber(fileVersionNumber);
            if (fileVersion == null) {
                throw new IOException("unsupported file version: " + fileVersionNumber);
            }
        } catch (final EOFException e) {
            // Empty file. Possible if the node crashed right after it created this file.
            stream.close();
            streamClosed = true;
        }
    }

    /**
     * Find the next event that should be returned.
     */
    private void findNext() throws IOException {
        while (next == null && !streamClosed) {

            final long initialCount = counter.getCount();

            try {
                final PlatformEvent candidate =
                        switch (fileVersion) {
                            case ORIGINAL -> EventSerializationUtils.deserializePlatformEvent(stream, true);
                            case PROTOBUF_EVENTS -> new PlatformEvent(stream.readPbjRecord(GossipEvent.PROTOBUF));
                        };
                if (candidate.getAncientIndicator(fileType) >= lowerBound) {
                    next = candidate;
                }
            } catch (final EOFException e) {
                if (counter.getCount() > initialCount) {
                    // We started parsing an event but couldn't find enough bytes to finish it.
                    // This is possible (if not likely) when a node is shut down abruptly.
                    hasPartialEvent = true;
                }
                stream.close();
                streamClosed = true;
            }
        }
    }

    /**
     * If true then this file contained a partial event. If false then the last event in the file was fully written when
     * the file was closed.
     */
    public boolean hasPartialEvent() {
        return hasPartialEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() throws IOException {
        findNext();
        return next != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public PlatformEvent next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException("no files remain in this iterator");
        }
        try {
            return next;
        } finally {
            next = null;
        }
    }
}
