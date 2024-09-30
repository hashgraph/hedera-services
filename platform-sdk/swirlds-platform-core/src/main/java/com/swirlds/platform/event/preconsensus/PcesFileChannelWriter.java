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
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes preconsensus events to a file using a {@link FileChannel}.
 */
public class PcesFileChannelWriter implements PcesFileWriter {
    /** The capacity of the ByteBuffer used to write events */
    private static final int BUFFER_CAPACITY = 1024 * 1024 * 10;
    /** The file channel for writing events */
    private final FileChannel channel;
    /** The buffer used to hold data being written to the file */
    private final ByteBuffer buffer;
    /** Wraps a ByteBuffer so that the protobuf codec can write to it */
    private final WritableSequentialData writableSequentialData;
    /** Tracks the size of the file in bytes */
    private int fileSize;

    /**
     * Create a new writer that writes events to a file using a {@link FileChannel}.
     *
     * @param filePath the path to the file to write to
     * @param syncEveryEvent if true, the file will be synced after every event is written
     * @throws IOException if an error occurs while opening the file
     */
    public PcesFileChannelWriter(@NonNull final Path filePath, final boolean syncEveryEvent) throws IOException {
        if (syncEveryEvent) {
            channel = FileChannel.open(
                    filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
        } else {
            channel = FileChannel.open(filePath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        buffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        writableSequentialData = BufferedData.wrap(buffer);
    }

    @Override
    public void writeVersion(final int version) throws IOException {
        buffer.clear();
        buffer.putInt(version);
        flipAndWrite();
    }

    @Override
    public void writeEvent(@NonNull final GossipEvent event) throws IOException {
        buffer.clear();
        buffer.putInt(GossipEvent.PROTOBUF.measureRecord(event));
        GossipEvent.PROTOBUF.write(event, writableSequentialData);
        flipAndWrite();
    }

    private void flipAndWrite() throws IOException {
        buffer.flip();
        final int bytesWritten = channel.write(buffer);
        fileSize += bytesWritten;
        if (bytesWritten != buffer.limit()) {
            throw new IOException(
                    "Failed to write data to file. Wrote " + bytesWritten + " bytes out of " + buffer.limit());
        }
    }

    @Override
    public void flush() throws IOException {
        // benchmarks show that this has horrible performance
        channel.force(false);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public long fileSize() {
        return fileSize;
    }
}
