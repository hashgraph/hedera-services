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

package com.swirlds.merkledb.files;

import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DataFileReaderCloseTest {

    private static DataFileCollection<long[]> collection;

    private static final DataItemSerializer<long[]> serializer = new TwoLongSerializer();

    @BeforeAll
    static void setUp() throws IOException {
        final Path dir = TemporaryFileBuilder.buildTemporaryFile("readerIsOpenTest");
        collection = new DataFileCollection<>(dir, "store", serializer, null);
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

    private static class TwoLongSerializer implements DataItemSerializer<long[]> {
        @Override
        public long getCurrentDataVersion() {
            return 0;
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
        public int getSerializedSize() {
            return Long.BYTES * 3;
        }

        @Override
        public int serialize(long[] data, ByteBuffer buffer) throws IOException {
            assert data.length == 2;
            buffer.putLong(data[0]);
            buffer.putLong(data[1]);
            return getSerializedSize();
        }

        @Override
        public long[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
            return new long[] {buffer.getLong(), buffer.getLong()};
        }
    }
}
