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
import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.common.test.fixtures.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomUtils.nextBoolean;
import static org.apache.commons.lang3.RandomUtils.nextDouble;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
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

    private Integer compactionLevel;
    private Long compactionTime;
    private Double savedSpace;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        String table = randomAlphabetic(7);
        coordinator = new MerkleDbCompactionCoordinator(
                table, statisticsUpdater, objectKeyToPath, hashStoreDisk, pathToHashKeyValue);
        coordinator.enableBackgroundCompaction();
        compactionLevel = nextInt(1, 6);
        compactionTime = nextLong(100, 10000);
        savedSpace = nextDouble(100, 1000);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCompactDiskStoreForObjectKeyToPathAsync(boolean compactionPassed)
            throws IOException, InterruptedException {
        testCompaction(
                objectKeyToPath,
                coordinator::compactDiskStoreForObjectKeyToPathAsync,
                (level, time) -> verify(statisticsUpdater).setLeafKeysStoreCompactionTimeMs(level, time),
                (level, savedSpace) ->
                        verify(statisticsUpdater).setLeafKeysStoreCompactionSavedSpaceMb(level, savedSpace),
                // expect compaction to be started
                true,
                // if compaction passed, the statistics should be updated
                compactionPassed,
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
                (level, time) -> verify(statisticsUpdater).setHashesStoreCompactionTimeMs(level, time),
                (level, savedSpace) ->
                        verify(statisticsUpdater).setHashesStoreCompactionSavedSpaceMb(level, savedSpace),
                // expect compaction to be started
                true,
                // if compaction passed, the statistics should be updated
                compactionPassed,
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
                (level, time) -> verify(statisticsUpdater).setLeavesStoreCompactionTimeMs(level, time),
                (level, savedSpace) ->
                        verify(statisticsUpdater).setLeavesStoreCompactionSavedSpaceMb(level, savedSpace),
                // expect compaction to be started
                true,
                // if compaction passed, the statistics should be updated
                compactionPassed,
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
                (level, time) -> verify(statisticsUpdater, never()).setLeafKeysStoreCompactionTimeMs(level, time),
                (level, savedSpace) ->
                        verify(statisticsUpdater, never()).setLeafKeysStoreCompactionSavedSpaceMb(level, savedSpace),
                // compaction shouldn't be started and statistic should not be updated because compaction is disabled
                false,
                nextBoolean(),
                false);
    }

    @Test
    void testCompactDiskStoreForHashesAsync_compactionDisabled() throws IOException, InterruptedException {
        stopAndDisableCompaction();
        testCompaction(
                hashStoreDisk,
                coordinator::compactDiskStoreForHashesAsync,
                (level, time) -> verify(statisticsUpdater, never()).setHashesStoreCompactionTimeMs(level, time),
                (level, savedSpace) ->
                        verify(statisticsUpdater, never()).setHashesStoreCompactionSavedSpaceMb(level, savedSpace),
                // compaction shouldn't be started and statistic should not be updated because compaction is disabled
                false,
                nextBoolean(),
                false);
    }

    @Test
    void testCompactPathToKeyValueAsync_compactionDisabled() throws IOException, InterruptedException {
        stopAndDisableCompaction();
        testCompaction(
                pathToHashKeyValue,
                coordinator::compactPathToKeyValueAsync,
                (level, time) -> verify(statisticsUpdater, never()).setLeavesStoreCompactionTimeMs(level, time),
                (level, savedSpace) ->
                        verify(statisticsUpdater, never()).setLeavesStoreCompactionSavedSpaceMb(level, savedSpace),
                // compaction shouldn't be started and statistic should not be updated because compaction is disabled
                false,
                nextBoolean(),
                false);
    }

    @Test
    void testCreateOrGetCompactingExecutor() throws ExecutionException, InterruptedException {
        MerkleDbCompactionCoordinator spy1 = spy(coordinator);
        MerkleDbCompactionCoordinator spy2 = spy(coordinator);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        when(spy1.getCompactingThreadNumber()).thenAnswer(invocation -> {
            latch1.await();
            return 1;
        });
        when(spy2.getCompactingThreadNumber()).thenAnswer(invocation -> {
            latch2.await();
            return 2;
        });
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Future<ExecutorService> service1Future = executorService.submit(spy1::createOrGetCompactingExecutor);
        Future<ExecutorService> service2Future = executorService.submit(spy2::createOrGetCompactingExecutor);
        latch1.countDown();
        ExecutorService executorService1 = service1Future.get();
        latch2.countDown();
        ExecutorService executorService2 = service2Future.get();

        assertSame(executorService1, executorService2);

        ExecutorService executor = coordinator.createOrGetCompactingExecutor();
        // if the executor is already created, the same instance should be returned
        assertSame(executor, coordinator.createOrGetCompactingExecutor());

        executor.shutdown();
        // if the executor is shutdown, a new instance should be returned
        ExecutorService newExecutor = coordinator.createOrGetCompactingExecutor();
        assertNotSame(executor, newExecutor);
        assertSame(newExecutor, coordinator.createOrGetCompactingExecutor());
    }

    @Test
    void testCompactionCancelled() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        initCompactibleMock(objectKeyToPath, nextBoolean(), latch);
        initCompactibleMock(pathToHashKeyValue, nextBoolean(), latch);
        initCompactibleMock(hashStoreDisk, nextBoolean(), latch);

        coordinator.compactDiskStoreForObjectKeyToPathAsync();
        coordinator.compactDiskStoreForHashesAsync();
        coordinator.compactPathToKeyValueAsync();

        // let all compactions get to the latch
        Thread.sleep(10);

        // latch is released by interruption of the compaction thread
        coordinator.stopAndDisableBackgroundCompaction();

        assertEventuallyDoesNotThrow(
                () -> {
                    try {
                        verify(objectKeyToPath).compact(any(), any());
                        verify(pathToHashKeyValue).compact(any(), any());
                        verify(hashStoreDisk).compact(any(), any());
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
            Compactable compactableToTest,
            Runnable methodCall,
            BiConsumer<Integer, Long> reportDurationMetricFunction,
            BiConsumer<Integer, Double> reportSavedSpaceMetricFunction,
            boolean expectCompactionStarted,
            boolean compactionPassed,
            boolean expectStatUpdate)
            throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        initCompactibleMock(compactableToTest, compactionPassed, latch);

        // run twice to make sure that the second call is discarded because one compaction is already in progress
        methodCall.run();
        methodCall.run();
        latch.countDown();

        assertCompactable(
                compactableToTest,
                reportDurationMetricFunction,
                reportSavedSpaceMetricFunction,
                expectCompactionStarted,
                expectStatUpdate);

        reset(objectKeyToPath, pathToHashKeyValue, hashStoreDisk, statisticsUpdater);
        initCompactibleMock(compactableToTest, compactionPassed, latch);

        // the second time it should succeed as well
        methodCall.run();
        assertCompactable(
                compactableToTest,
                reportDurationMetricFunction,
                reportSavedSpaceMetricFunction,
                expectCompactionStarted,
                expectStatUpdate);
    }

    private void testCompactionFailed(Compactable compactableToTest, Runnable methodCall)
            throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        when(compactableToTest.compact(ArgumentMatchers.any(), any())).thenAnswer(invocation -> {
            latch.await();
            throw new RuntimeException("testCompactionFailed");
        });

        // run twice to make sure that the second call is discarded because one compaction is already in progress
        methodCall.run();
        latch.countDown();

        assertEventuallyDoesNotThrow(
                () -> {
                    try {
                        verify(compactableToTest).compact(any(), any());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    verifyNoInteractions(statisticsUpdater);
                },
                Duration.ofMillis(100),
                "Unexpected mock state");
    }

    @SuppressWarnings("unchecked")
    private void initCompactibleMock(Compactable compactableToTest, boolean compactionPassed, CountDownLatch latch)
            throws IOException, InterruptedException {
        when(compactableToTest.compact(ArgumentMatchers.any(), any())).thenAnswer(invocation -> {
            latch.await();
            invocation.getArgument(0, BiConsumer.class).accept(compactionLevel, compactionTime);
            invocation.getArgument(1, BiConsumer.class).accept(compactionLevel, savedSpace);
            return compactionPassed;
        });
    }

    private void assertCompactable(
            Compactable compactableToTest,
            BiConsumer<Integer, Long> reportDurationMetricFunction,
            BiConsumer<Integer, Double> reportSavedSpaceMetricFunction,
            boolean expectCompactionStarted,
            boolean expectStatUpdate) {
        assertEventuallyDoesNotThrow(
                () -> {
                    reportDurationMetricFunction.accept(compactionLevel, compactionTime);
                    reportSavedSpaceMetricFunction.accept(compactionLevel, savedSpace);
                    VerificationMode compactionVerificationMode = expectCompactionStarted ? times(1) : never();
                    try {
                        verify(compactableToTest, compactionVerificationMode).compact(any(), any());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    VerificationMode statVerificationMode = expectStatUpdate ? times(1) : never();
                    verify(statisticsUpdater, statVerificationMode).updateStoreFileStats();
                    verify(statisticsUpdater, statVerificationMode).updateOffHeapStats();
                },
                Duration.ofMillis(100),
                "Unexpected mock state");
    }
}
