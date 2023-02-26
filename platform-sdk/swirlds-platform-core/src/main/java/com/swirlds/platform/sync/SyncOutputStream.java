/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

import static com.swirlds.common.io.extendable.ExtendableOutputStream.extendOutputStream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.ByteConstants;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SyncOutputStream extends SerializableDataOutputStream {
    private final CountingStreamExtension syncByteCounter;
    private final CountingStreamExtension connectionByteCounter;
    private final AtomicReference<Instant> requestSent;

    protected SyncOutputStream(
            OutputStream out, CountingStreamExtension syncByteCounter, CountingStreamExtension connectionByteCounter) {
        super(out);
        this.syncByteCounter = syncByteCounter;
        this.connectionByteCounter = connectionByteCounter;
        this.requestSent = new AtomicReference<>(null);
    }

    public static SyncOutputStream createSyncOutputStream(OutputStream out, int bufferSize) {
        CountingStreamExtension syncByteCounter = new CountingStreamExtension();
        CountingStreamExtension connectionByteCounter = new CountingStreamExtension();

        // we write the data to the buffer first, for efficiency
        return new SyncOutputStream(
                new BufferedOutputStream(extendOutputStream(out, syncByteCounter, connectionByteCounter), bufferSize),
                syncByteCounter,
                connectionByteCounter);
    }

    public CountingStreamExtension getSyncByteCounter() {
        return syncByteCounter;
    }

    public CountingStreamExtension getConnectionByteCounter() {
        return connectionByteCounter;
    }

    /**
     * @return the time the last sync request was sent
     */
    public Instant getRequestSentTime() {
        return requestSent.get();
    }

    /**
     * Send a sync request
     *
     * @throws IOException
     * 		if a stream exception occurs
     */
    public void requestSync() throws IOException {
        writeByte(ByteConstants.COMM_SYNC_REQUEST);
        requestSent.set(Instant.now());
    }

    /**
     * Accepts a previously requested sync
     *
     * @throws IOException
     * 		if a stream exception occurs
     */
    public void acceptSync() throws IOException {
        writeByte(ByteConstants.COMM_SYNC_ACK);
    }

    /**
     * Rejects a previously requested sync
     *
     * @throws IOException
     * 		if a stream exception occurs
     */
    public void rejectSync() throws IOException {
        writeByte(ByteConstants.COMM_SYNC_NACK);
    }

    /**
     * Write this node's generation numbers to an output stream
     *
     * @throws IOException
     * 		if a stream exception occurs
     */
    public void writeGenerations(final Generations generations) throws IOException {
        writeSerializable(generations, false);
    }

    /**
     * Write to the {@link SyncOutputStream} the hashes of the tip events from this node's shadow graph
     *
     * @throws IOException
     * 		iff the {@link SyncOutputStream} throws
     */
    public void writeTipHashes(final List<Hash> tipHashes) throws IOException {
        writeSerializableList(tipHashes, false, true);
    }

    /**
     * Write event data
     *
     * @param event
     * 		the event to write
     * @throws IOException
     * 		iff the {@link SyncOutputStream} instance throws
     */
    public void writeEventData(final EventImpl event) throws IOException {
        writeSerializable(event.getBaseEvent(), false);
    }
}
