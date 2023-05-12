/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.stream;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import com.swirlds.common.stream.internal.SingleStreamIterator;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Is used for testing {@link SingleStreamIterator}
 *
 * writes Hash and objects to a stream;
 */
class WriteToStreamConsumer implements LinkedObjectStream<ObjectForTestStream> {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(WriteToStreamConsumer.class);

    private static final Marker LOGM_OBJECT_STREAM = MarkerManager.getMarker("OBJECT_STREAM");
    private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");
    /**
     * the stream to which we write RunningHash and serialize objects
     */
    private SerializableDataOutputStream outputStream;

    /**
     * current runningHash
     */
    private RunningHash runningHash;

    boolean isClosed = false;

    int consumedCount = 0;

    public WriteToStreamConsumer(SerializableDataOutputStream stream, Hash startRunningHash) throws IOException {
        this.outputStream = stream;
        try {
            this.outputStream.writeSerializable(startRunningHash, true);
        } catch (IOException ex) {
            throw new IOException(
                    String.format(
                            "Failed to write startRunningHash: %s. IOException: %s", startRunningHash, ex.getMessage()),
                    ex.getCause());
        }

        logger.info(LOGM_OBJECT_STREAM, "Wrote startRunningHash: {}", startRunningHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addObject(ObjectForTestStream object) {
        try {
            outputStream.writeSerializable(object, true);
            logger.info(LOGM_OBJECT_STREAM, "write object: {}", object);
            // update runningHash
            this.runningHash = object.getRunningHash();
            consumedCount++;
        } catch (IOException ex) {
            logger.error(LOGM_EXCEPTION, "Failed to add object", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            outputStream.writeSerializable(this.runningHash.getFutureHash().getAndRethrow(), true);
            logger.info(LOGM_OBJECT_STREAM, "Wrote endRunningHash: {}", this.runningHash);
            outputStream.close();
            isClosed = true;
        } catch (IOException ex) {
            logger.error(LOGM_EXCEPTION, "Failed to close output stream", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        try {
            outputStream.close();
        } catch (IOException ex) {
            logger.error(LOGM_EXCEPTION, "Failed to close output stream", ex);
        }
    }

    @Override
    public void setRunningHash(final Hash hash) {
        this.runningHash = new RunningHash(hash);
    }
}
