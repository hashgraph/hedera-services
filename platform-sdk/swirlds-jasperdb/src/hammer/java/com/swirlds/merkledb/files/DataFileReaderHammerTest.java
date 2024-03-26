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
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.serialize.DataItemHeader;
import com.swirlds.merkledb.serialize.DataItemSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DataFileReaderHammerTest {

    @Test
    @DisplayName("Test DataFileReader with interrupts")
    void interruptedReadsHammerTest() throws Exception {
        final int fileSize = 10_000_000;
        final int itemCount = 1_000;
        final int itemSize = fileSize / itemCount;
        final int readerThreads = 32;
        final int readIterations = 10_000;

        final Path tempFile = TemporaryFileBuilder.buildTemporaryFile("interruptedReadsHammerTest");
        final ByteBuffer writeBuf = ByteBuffer.allocate(itemSize);
        for (int i = 0; i < itemSize; i++) {
            writeBuf.put((byte) (i % 100));
        }
        final FileChannel writeChannel =
                FileChannel.open(tempFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        for (int i = 0; i < itemCount; i++) {
            writeBuf.clear();
            writeChannel.write(writeBuf);
        }
        writeChannel.close();

        final ExecutorService exec = Executors.newFixedThreadPool(readerThreads);
        final Random rand = new Random();
        final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);
        final DataFileMetadata metadata =
                new DataFileMetadata(itemCount, 0, Instant.now(), 0, INITIAL_COMPACTION_LEVEL);
        final DataFileReader<byte[]> dataReader =
                new DataFileReaderPbj<>(dbConfig, tempFile, new TestDataItemSerializer(itemSize), metadata);
        final AtomicInteger activeReaders = new AtomicInteger(readerThreads);
        final AtomicReferenceArray<Thread> threads = new AtomicReferenceArray<>(readerThreads);
        final Future<?>[] jobs = new Future[readerThreads];
        for (int i = 0; i < readerThreads; i++) {
            final int threadNo = i;
            final Future<?> result = exec.submit(() -> {
                threads.set(threadNo, Thread.currentThread());
                try {
                    for (int j = 0; j < readIterations; j++) {
                        try {
                            final int start = rand.nextInt(itemCount) * itemSize;
                            final byte[] data = dataReader.readDataItem(start);
                            Assertions.assertNotNull(data);
                            for (int k = 0; k < itemSize; k++) {
                                Assertions.assertEquals(k % 100, data[k]);
                            }
                        } catch (final ClosedByInterruptException e) {
                            Thread.interrupted(); // clear "interrupted" status and keep going
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } finally {
                    activeReaders.decrementAndGet();
                }
            });
            jobs[i] = result;
        }
        // Add chaos: interrupt random threads, but not same thread twice in a row
        int lastInterruptedThread = -1;
        while (activeReaders.get() > 0) {
            try {
                // Don't interrupt too often
                Thread.sleep(rand.nextInt(100) + 300);
            } catch (final InterruptedException e) {
                // ignore
            }
            final int threadToInterrupt = rand.nextInt(readerThreads);
            if (threadToInterrupt != lastInterruptedThread) {
                final Thread thread = threads.get(threadToInterrupt);
                if (thread != null) {
                    thread.interrupt();
                }
                lastInterruptedThread = threadToInterrupt;
            }
        }
        // Check for exceptions
        for (int i = 0; i < readerThreads; i++) {
            Future<?> result = jobs[i];
            result.get();
        }
    }

    private static class TestDataItemSerializer implements DataItemSerializer<byte[]> {
        private final int size;

        public TestDataItemSerializer(final int size) {
            this.size = size;
        }

        @Override
        public long getCurrentDataVersion() {
            return 0;
        }

        @Override
        public int getSerializedSize() {
            return size;
        }

        @Override
        public int getHeaderSize() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public DataItemHeader deserializeHeader(ByteBuffer buffer) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void serialize(byte[] data, WritableSequentialData out) {
            out.writeBytes(data);
        }

        @Override
        public void serialize(byte[] data, ByteBuffer buffer) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public byte[] deserialize(ReadableSequentialData in) {
            final byte[] r = new byte[size];
            in.readBytes(r);
            return r;
        }

        @Override
        public byte[] deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}
