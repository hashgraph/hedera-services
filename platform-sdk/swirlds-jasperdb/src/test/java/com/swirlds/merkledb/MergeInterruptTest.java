/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyFalse;
import static com.swirlds.merkledb.MerkleDbTestUtils.runTaskAndCleanThreadLocals;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The idea of these tests is to make sure that the merging method when run in a background thread can be interrupted
 * and stops correctly. Even when it is blocked paused waiting on snapshot process.
 */
class MergeInterruptTest {

    /** This needs to be big enough so that the snapshot is slow enough that we can do a merge at the same time */
    private static final int COUNT = 1_000_000;

    /**
     * Temporary directory provided by JUnit
     */
    @SuppressWarnings("unused")
    @TempDir
    Path tmpFileDir;

    /*
     * RUN THE TEST IN A BACKGROUND THREAD. We do this so that we can kill the thread at the end of the test which will
     * clean up all thread local caches held.
     */
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void startMergeThenInterrupt() throws Exception {
        runTaskAndCleanThreadLocals(this::startMergeThenInterruptImpl);
    }

    /**
     * Run a test to do a merge, and then call stopBackgroundCompaction(). The expected result is the merging thread
     * should be interrupted and end quickly.
     */
    boolean startMergeThenInterruptImpl() throws IOException, InterruptedException {
        final Path storeDir = tmpFileDir.resolve("startMergeThenInterruptImpl");
        final MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource =
                TestType.fixed_fixed.dataType().createDataSource(storeDir, "mergeThenInterrupt", COUNT, 0, false, true);
        try {
            // create some internal and leaf nodes in batches
            createData(dataSource);
            // start merging
            final AtomicBoolean mergeResult = new AtomicBoolean();
            final Thread mergingThread = new Thread(() -> mergeResult.set(dataSource.doMerge()));
            mergingThread.start();
            // wait a small-time for merging to start
            MILLISECONDS.sleep(20);
            // now request it is stopped
            mergingThread.interrupt();
            // wait for merging thread to finish, and it should return false because it was interrupted
            assertEventuallyFalse(mergingThread::isAlive, Duration.ofSeconds(5), "merging thread should have finished");
            // result should have been false as it should have stopped before completing successfully
            assertFalse(mergeResult.get(), "Merging thread should have stopped");
        } finally {
            dataSource.close();
        }
        return true;
    }

    /*
     * RUN THE TEST IN A BACKGROUND THREAD. We do this so that we can kill the thread at the end of the test which will
     * clean up all thread local caches held.
     */
    @Test
    void startMergeWhileSnapshottingThenInterrupt() throws Exception {
        runTaskAndCleanThreadLocals(this::startMergeWhileSnapshottingThenInterruptImpl);
    }

    /**
     * Run a test to do a merge while in the middle of snapshotting, and then call stopBackgroundCompaction() and close
     * the database. The expected result is the merging thread should be interrupted and end immediately and not be
     * blocked by the snapshotting. Check to make sure the database closes immediately and is not blocked waiting for
     * merge thread to exit. DataFileCommon.waitIfMergingPaused() used to eat interrupted exceptions that would block
     * the merging thread from a timely exit and fail this test.
     */
    boolean startMergeWhileSnapshottingThenInterruptImpl() throws IOException, InterruptedException {
        final Path storeDir = tmpFileDir.resolve("startMergeWhileSnapshottingThenInterruptImpl");
        final MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource = TestType.fixed_fixed
                .dataType()
                .createDataSource(storeDir, "mergeWhileSnapshotting", COUNT, 0, false, true);
        final ExecutorService exec = Executors.newCachedThreadPool();
        try {
            // create some internal and leaf nodes in batches
            createData(dataSource);
            // create a snapshot
            final Path snapshotDir = tmpFileDir.resolve("startMergeWhileSnapshottingThenInterruptImplSnapshot");
            exec.submit(() -> {
                dataSource.snapshot(snapshotDir);
                return null;
            });
            // start merging
            final AtomicBoolean mergeResult = new AtomicBoolean();
            final Thread mergingThread = new Thread(() -> mergeResult.set(dataSource.doMerge()));
            mergingThread.start();
            // wait a small-time for merging to start
            MILLISECONDS.sleep(20);
            // now request it is stopped
            mergingThread.interrupt();
            // wait for merging thread to finish, and it should return false because it was interrupted
            assertEventuallyFalse(mergingThread::isAlive, Duration.ofSeconds(5), "merging thread should have finished");
            // result should have been false as it should have stopped before completing successfully
            assertFalse(mergeResult.get(), "Merging thread should have stopped");
        } finally {
            dataSource.close();
            exec.shutdown();
            assertTrue(exec.awaitTermination(30, TimeUnit.SECONDS), "Should not timeout");
        }
        return true;
    }

    private void createData(final MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource)
            throws IOException {
        final int count = COUNT / 10;
        for (int batch = 0; batch < 10; batch++) {
            final int start = batch * count;
            final int end = start + count;
            final int lastLeafPath = (COUNT + end) - 1;
            dataSource.saveRecords(
                    COUNT,
                    lastLeafPath,
                    IntStream.range(start, end).mapToObj(MerkleDbDataSourceTest::createVirtualInternalRecord),
                    IntStream.range(COUNT + start, COUNT + end)
                            .mapToObj(i -> TestType.fixed_fixed.dataType().createVirtualLeafRecord(i)),
                    Stream.empty());
        }
    }
}
