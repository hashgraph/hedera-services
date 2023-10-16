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

package com.swirlds.merkledb;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyDoesNotThrow;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomUtils.nextBoolean;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swirlds.merkledb.files.MemoryIndexDiskKeyValueStore;
import com.swirlds.merkledb.files.hashmap.HalfDiskHashMap;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

class MerkleDbCompactionCoordinatorTest {

    @Mock
    private MerkleDbStatisticsUpdater statisticsUpdater;

    @Mock
    private HalfDiskHashMap<VirtualKey> objectKeyToPath;

    @Mock
    private MemoryIndexDiskKeyValueStore<VirtualHashRecord> hashStoreDisk;

    @Mock
    private MemoryIndexDiskKeyValueStore<VirtualLeafRecord<VirtualKey, VirtualValue>> pathToHashKeyValue;

    private MerkleDbCompactionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        String table = randomAlphabetic(7);
        coordinator = new MerkleDbCompactionCoordinator(table, objectKeyToPath, hashStoreDisk, pathToHashKeyValue);
        coordinator.enableBackgroundCompaction();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCompactDiskStoreForObjectKeyToPathAsync(boolean compactionPassed)
            throws IOException, InterruptedException {
        testCompaction(
                objectKeyToPath,
                coordinator::compactDiskStoreForObjectKeyToPathAsync,
                // expect compaction to be started
                true,
                compactionPassed);
    }

    @Test
    void testCompactDiskStoreForObjectKeyToPathAsync_failed() throws IOException, InterruptedException {
        testCompactionFailed(objectKeyToPath, coordinator::compactDiskStoreForObjectKeyToPathAsync);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCompactDiskStoreForHashesAsync(boolean compactionPassed) throws IOException, InterruptedException {
        testCompaction(
                hashStoreDisk,
                coordinator::compactDiskStoreForHashesAsync,
                // expect compaction to be started
                true,
                compactionPassed);
    }

    @Test
    void testCompactDiskStoreForHashesAsync_failed() throws IOException, InterruptedException {
        testCompactionFailed(hashStoreDisk, coordinator::compactDiskStoreForHashesAsync);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCompactPathToKeyValueAsync(boolean compactionPassed) throws IOException, InterruptedException {
        testCompaction(
                pathToHashKeyValue,
                coordinator::compactPathToKeyValueAsync,
                // expect compaction to be started
                true,
                compactionPassed);
    }

    @Test
    void testCompactPathToKeyValueAsync_failed() throws IOException, InterruptedException {
        testCompactionFailed(pathToHashKeyValue, coordinator::compactPathToKeyValueAsync);
    }

    @Test
    void testCompactDiskStoreForObjectKeyToPathAsync_compactionDisabled() throws IOException, InterruptedException {
        stopAndDisableCompaction();
        testCompaction(
                objectKeyToPath,
                coordinator::compactDiskStoreForObjectKeyToPathAsync,
                // compaction shouldn't be started
                false,
                nextBoolean());
    }

    @Test
    void testCompactDiskStoreForHashesAsync_compactionDisabled() throws IOException, InterruptedException {
        stopAndDisableCompaction();
        testCompaction(
                hashStoreDisk,
                coordinator::compactDiskStoreForHashesAsync,
                // compaction shouldn't be started
                false,
                nextBoolean());
    }

    @Test
    void testCompactPathToKeyValueAsync_compactionDisabled() throws IOException, InterruptedException {
        stopAndDisableCompaction();
        testCompaction(
                pathToHashKeyValue,
                coordinator::compactPathToKeyValueAsync,
                // compaction shouldn't be started
                false,
                nextBoolean());
    }

    @Test
    void testCompactionCancelled() throws IOException, InterruptedException {
        CountDownLatch compactLatch = new CountDownLatch(1);
        CountDownLatch testLatch = new CountDownLatch(3);
        initCompactibleMock(objectKeyToPath, nextBoolean(), testLatch, compactLatch);
        initCompactibleMock(pathToHashKeyValue, nextBoolean(), testLatch, compactLatch);
        initCompactibleMock(hashStoreDisk, nextBoolean(), testLatch, compactLatch);

        coordinator.compactDiskStoreForObjectKeyToPathAsync();
        coordinator.compactDiskStoreForHashesAsync();
        coordinator.compactPathToKeyValueAsync();

        // let all compactions get to the latch
        testLatch.await();

        // latch is released by interruption of the compaction thread
        coordinator.stopAndDisableBackgroundCompaction();

        assertEventuallyDoesNotThrow(
                () -> {
                    try {
                        verify(objectKeyToPath).compact();
                        verify(pathToHashKeyValue).compact();
                        verify(hashStoreDisk).compact();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    verifyNoInteractions(statisticsUpdater);
                },
                Duration.ofMillis(100),
                "Unexpected mock state");
    }

    @Test
    void testCompactionWithNullNullables() throws IOException, InterruptedException {
        String table = randomAlphabetic(7);
        coordinator = new MerkleDbCompactionCoordinator(table, null, null, pathToHashKeyValue);
        coordinator.enableBackgroundCompaction();

        testCompaction(
                pathToHashKeyValue,
                coordinator::compactPathToKeyValueAsync,
                // expect compaction to be started
                true,
                true);

        reset(pathToHashKeyValue);

        CountDownLatch compactLatch = new CountDownLatch(1);
        CountDownLatch testLatch = new CountDownLatch(1);
        initCompactibleMock(pathToHashKeyValue, nextBoolean(), testLatch, compactLatch);

        coordinator.compactPathToKeyValueAsync();

        // let all compactions get to the latch
        testLatch.await();

        // latch is released by interruption of the compaction thread
        coordinator.stopAndDisableBackgroundCompaction();

        assertEventuallyDoesNotThrow(
                () -> {
                    try {
                        verify(pathToHashKeyValue).compact();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    verifyNoInteractions(statisticsUpdater);
                },
                Duration.ofMillis(100),
                "Unexpected mock state");
    }

    private void stopAndDisableCompaction() {
        assertTrue(coordinator.isCompactionEnabled());
        coordinator.stopAndDisableBackgroundCompaction();
        assertFalse(coordinator.isCompactionEnabled());
    }

    private void testCompaction(
            Compactible compactibleToTest,
            Runnable methodCall,
            boolean expectCompactionStarted,
            boolean compactionPassed)
            throws IOException, InterruptedException {
        CountDownLatch testLatch = new CountDownLatch(1);
        CountDownLatch compactLatch = new CountDownLatch(1);
        initCompactibleMock(compactibleToTest, compactionPassed, testLatch, compactLatch);

        // run twice to make sure that the second call is discarded because one compaction is already in progress
        methodCall.run();
        methodCall.run();
        if (expectCompactionStarted) {
            testLatch.await();
        }
        compactLatch.countDown();

        assertCompactable(compactibleToTest, expectCompactionStarted);

        reset(objectKeyToPath, pathToHashKeyValue, hashStoreDisk, statisticsUpdater);
        initCompactibleMock(compactibleToTest, compactionPassed, testLatch, compactLatch);

        // the second time it should succeed as well
        methodCall.run();
        assertCompactable(compactibleToTest, expectCompactionStarted);
    }

    private void testCompactionFailed(Compactible compactibleToTest, Runnable methodCall)
            throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        when(compactibleToTest.compact()).thenAnswer(invocation -> {
            latch.await();
            throw new RuntimeException("testCompactionFailed");
        });

        // run twice to make sure that the second call is discarded because one compaction is already in progress
        methodCall.run();
        latch.countDown();

        assertEventuallyDoesNotThrow(
                () -> {
                    try {
                        verify(compactibleToTest).compact();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    verifyNoInteractions(statisticsUpdater);
                },
                Duration.ofMillis(100),
                "Unexpected mock state");
    }

    @SuppressWarnings("unchecked")
    private void initCompactibleMock(
            Compactible compactibleToTest,
            boolean compactionPassed,
            CountDownLatch testLatch,
            CountDownLatch compactLatch)
            throws IOException, InterruptedException {
        when(compactibleToTest.compact()).thenAnswer(invocation -> {
            testLatch.countDown();
            compactLatch.await();
            return compactionPassed;
        });
    }

    private void assertCompactable(Compactible compactibleToTest, boolean expectCompactionStarted) {
        assertEventuallyDoesNotThrow(
                () -> {
                    VerificationMode compactionVerificationMode = expectCompactionStarted ? times(1) : never();
                    try {
                        verify(compactibleToTest, compactionVerificationMode).compact();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                Duration.ofMillis(100),
                "Unexpected mock state");
    }
}
