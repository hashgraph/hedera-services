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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;

@Disabled
class DataFileWriterTest {

    private DataFileWriter dataFileWriter;

    @BeforeEach
    public void setUp() throws Exception {
        Path dataFileWriterPath = Files.createTempDirectory("dataFileWriter");
        dataFileWriter = new DataFileWriter("test", dataFileWriterPath, 1, Instant.now(), 1);
    }

    /**
     * This test reproduces rare scenario when `moveMmapBuffer` method is called on interrupted thread.
     * It will result in writingChannel.map(MapMode.READ_WRITE, mmapPositionInFile, MMAP_BUF_SIZE) call returning null.
     * DataFileWriter has to handle this case gracefully.
     */
    @RepeatedTest(100) // it takes several iterations to reproduce the issue
    public void testMoveMmapBufferOnInterruptedThread() {
        ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
        Future<?> writeFuture = writeExecutor.submit(() -> {
            try {
                BufferedData allocate = BufferedData.allocate(10);
                allocate.writeBytes("test".getBytes());
                allocate.flip();
                dataFileWriter.storeDataItem(allocate);
            } catch (IOException e) {
                throw new RuntimeException();
            }
        });

        // at this point a thread in writeExecutor is blocked on serializeLatch.await()

        ExecutorService finishExecutor = Executors.newSingleThreadExecutor();
        Future<?> finishWritingFuture = finishExecutor.submit(() -> {
            try {
                dataFileWriter.finishWriting();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // At this point a thread in finishExecutor is blocked on finishWriting because a thread from
        // writeExecutor holds the lock.

        // Releasing the latch by interrupting the thread in writeExecutor
        writeFuture.cancel(true);

        assertDoesNotThrow(() -> finishWritingFuture.get());
    }
}
