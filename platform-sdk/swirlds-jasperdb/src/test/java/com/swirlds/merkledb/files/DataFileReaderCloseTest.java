/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.files.DataFileCompactor.INITIAL_COMPACTION_LEVEL;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataFileReaderCloseTest {

    private static DataFileCollection<long[]> collection;

    private static final DataItemSerializer<long[]> serializer = new TwoLongSerializer();

    @BeforeAll
    static void setup() throws IOException {
        final Path dir = TemporaryFileBuilder.buildTemporaryFile("readerIsOpenTest");
        final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
        collection = new DataFileCollection<>(dbConfig, dir, "store", serializer, null);
    }

    @AfterAll
    static void teardown() throws IOException {
        collection.close();
    }

    @Test
    void readerIsOpenTest() throws Exception {
        final int COUNT = 100;
        collection.startWriting();
        final LongList index = new LongListOffHeap();
        for (int i = 0; i < COUNT; i++) {
            index.put(i, collection.storeDataItem(new long[] {i, i + 1}));
        }
        //noinspection resource
        collection.endWriting(0, COUNT - 1);
        final AtomicBoolean readingThreadStarted = new AtomicBoolean(false);
        final AtomicReference<IOException> exceptionOccurred = new AtomicReference<>();
        final Thread readingThread = new Thread(() -> {
            final Random rand = new Random();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    final int i = rand.nextInt(COUNT);
                    final long dataLocation = index.get(i);
                    final long[] item = collection.readDataItem(dataLocation);
                    Assertions.assertEquals(i, item[0]);
                    Assertions.assertEquals(i + 1, item[1]);
                    readingThreadStarted.set(true);
                }
            } catch (final ClosedByInterruptException e) {
                // This is expected
            } catch (final IOException e) {
                exceptionOccurred.set(e);
            }
        });
        readingThread.start();
        while (!readingThreadStarted.get()) {
            //noinspection BusyWait
            Thread.sleep(1);
        }
        readingThread.interrupt();
        readingThread.join(4000);
        if (exceptionOccurred.get() != null) {
            exceptionOccurred.get().printStackTrace();
            Assertions.assertNull(exceptionOccurred.get(), "No IOException is expected");
        }
        for (int i = 0; i < COUNT; i++) {
            final long[] item = collection.readDataItemUsingIndex(index, i);
            Assertions.assertEquals(i, item[0]);
            Assertions.assertEquals(i + 1, item[1]);
        }
    }

    @Test
    void readWhileFinishWritingTest() throws IOException {
        final Path tmpDir = TemporaryFileBuilder.buildTemporaryDirectory("readWhileFinishWritingTest");
        final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
        for (int i = 0; i < 100; i++) {
            Path filePath = null;
            try {
                final DataFileWriterPbj<long[]> writer =
                        new DataFileWriterPbj<>("test", tmpDir, i, serializer, Instant.now(), INITIAL_COMPACTION_LEVEL);
                filePath = writer.getPath();
                final DataFileMetadata metadata = writer.getMetadata();
                final LongList index = new LongListOffHeap();
                index.put(0, writer.storeDataItem(new long[] {i, i * 2 + 1}));
                final DataFileReaderPbj<long[]> reader =
                        new DataFileReaderPbj<>(dbConfig, filePath, serializer, metadata);
                final int fi = i;
                // Check the item in parallel to finish writing
                IntStream.of(0, 1).parallel().forEach(t -> {
                    try {
                        if (t == 1) {
                            writer.finishWriting();
                        } else {
                            final long[] item = reader.readDataItem(index.get(0));
                            Assertions.assertEquals(fi, item[0]);
                            Assertions.assertEquals(fi * 2 + 1, item[1]);
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } finally {
                if (filePath != null) {
                    Files.delete(filePath);
                }
            }
        }
    }

    private static class TwoLongSerializer implements DataItemSerializer<long[]> {
        @Override
        public long getCurrentDataVersion() {
            return 0;
        }

        @Override
        public int getSerializedSize() {
            return Long.BYTES * 2;
        }

        @Override
        public int getHeaderSize() {
            return Long.BYTES;
        }

        @Override
        public DataItemHeader deserializeHeader(ByteBuffer buffer) {
            return new DataItemHeader(getSerializedSize(), buffer.getLong());
        }

        @Override
        public void serialize(long[] data, WritableSequentialData out) {
            assert data.length == 2;
            out.writeLong(data[0]);
            out.writeLong(data[1]);
        }

        @Override
        public void serialize(long[] data, ByteBuffer buffer) throws IOException {
            assert data.length == 2;
            buffer.putLong(data[0]);
            buffer.putLong(data[1]);
        }

        @Override
        public long[] deserialize(ReadableSequentialData in) {
            return new long[] {in.readLong(), in.readLong()};
        }

        @Override
        public long[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
            return new long[] {buffer.getLong(), buffer.getLong()};
        }
    }
}
