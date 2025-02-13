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

package com.swirlds.platform.event.preconsensus;

import com.hedera.hapi.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * Interface for writing events to a file.
 */
public interface PcesFileWriter {

    /**
     * Write the version of the file format to the file.
     *
     * @param version the version of the file format
     */
    void writeVersion(final int version) throws IOException;

    /**
     * Write an event to the file.
     *
     * @param event the event to write
     */
    void writeEvent(@NonNull final GossipEvent event) throws IOException;

    /**
     * Flush the file.
     */
    void flush() throws IOException;

    /**
     * Sync the buffer with the file system.
     */
    void sync() throws IOException;

    /**
     * Close the file.
     */
    void close() throws IOException;

    /**
     * @return the size of the file in bytes
     */
    long fileSize();
}
