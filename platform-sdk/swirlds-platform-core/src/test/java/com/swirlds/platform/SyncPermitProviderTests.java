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

package com.swirlds.platform;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.gossip.sync.SyncPermitProvider;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SyncPermitProvider Tests")
class SyncPermitProviderTests {

    private PlatformContext buildContext(@NonNull final int permitCount) {
        return TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue("sync.syncProtocolPermitCount", permitCount)
                        .getOrCreateConfig())
                .build();
    }

    @Test
    @DisplayName("Permits are acquired and released properly")
    void testPermitRelease() {
        final int numPermits = 3;
        final SyncPermitProvider syncPermitProvider = new SyncPermitProvider(buildContext(numPermits), () -> 0);

        assertEquals(numPermits, syncPermitProvider.getAvailablePermitCount(), "all permits should be available");

        assertTrue(syncPermitProvider.tryAcquire(), "first acquire should succeed");
        assertEquals(
                numPermits - 1,
                syncPermitProvider.getAvailablePermitCount(),
                "one less permit should be available when a permit is acquired");

        syncPermitProvider.release();
        assertEquals(
                numPermits,
                syncPermitProvider.getAvailablePermitCount(),
                "all permits should be available after the acquired permit is released");
    }

    @Test
    @DisplayName("Once all permits are acquired, further attempts to acquire fail")
    void testAllPermitsAcquired() {
        final int numPermits = 9;
        final SyncPermitProvider syncPermitProvider = new SyncPermitProvider(buildContext(numPermits), () -> 0);

        assertEquals(numPermits, syncPermitProvider.getAvailablePermitCount(), "all permits should be available");

        // Acquire all the permits
        for (int i = 0; i < numPermits; i++) {
            assertTrue(syncPermitProvider.tryAcquire(), "first acquire should succeed");
            assertEquals(
                    numPermits - i - 1,
                    syncPermitProvider.getAvailablePermitCount(),
                    "one less permit should be available when a permit is acquired");
        }

        // Attempts to acquire more permits should fail
        assertFalse(syncPermitProvider.tryAcquire(), "no further permits should be able to be acquired");

        // Releasing permits should result in more permits being available
        for (int i = 0; i < numPermits; i++) {
            syncPermitProvider.release();
            assertEquals(
                    i + 1,
                    syncPermitProvider.getAvailablePermitCount(),
                    "one more permit should be available when a permit is released");
        }
    }

    @Test
    @DisplayName("waitForAllSyncsToFinish blocks until all permits are released")
    void testWaitForAllSyncsToFinish() {
        final int numPermits = 3;
        final SyncPermitProvider syncPermitProvider = new SyncPermitProvider(buildContext(numPermits), () -> 0);

        // Acquire all the permits
        for (int i = 0; i < numPermits; i++) {
            assertTrue(syncPermitProvider.tryAcquire());
        }

        // Attempts to acquire more permits should fail
        assertFalse(syncPermitProvider.tryAcquire(), "no further permits should be able to be acquired");

        final AtomicBoolean waitComplete = new AtomicBoolean(false);

        // Have a separate thread wait for syncs to finish
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            syncPermitProvider.acquireAndReleaseAll();
            waitComplete.set(true);
            return null;
        });

        try {
            // wait a bit, to give waitForAllSyncsToFinish time to potentially misbehave
            MILLISECONDS.sleep(50);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(waitComplete.get(), "waitForAllSyncsToFinish should not return until all permits are released");

        // close the permits that have already been acquired, so waitForAllSyncsToFinish will return
        for (int i = 0; i < numPermits; i++) {
            syncPermitProvider.release();
        }

        assertEventuallyTrue(
                waitComplete::get,
                Duration.ofMillis(1000),
                "waitForAllSyncsToFinish should return after all permits are released");

        executorService.shutdown();
    }
}
