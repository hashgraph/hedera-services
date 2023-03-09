/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap;

import static com.swirlds.common.io.utility.FileUtils.deleteDirectory;
import static com.swirlds.common.merkle.iterators.MerkleIterationOrder.BREADTH_FIRST;
import static com.swirlds.test.framework.ResourceLoader.loadLog4jContext;
import static com.swirlds.virtualmap.VirtualMapTestUtils.createMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.test.fixtures.config.TestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.virtualmap.datasource.InMemoryDataSource;
import com.swirlds.virtualmap.internal.merkle.VirtualLeafNode;
import com.swirlds.virtualmap.internal.merkle.VirtualMapStatistics;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("ALL")
class VirtualMapTests extends VirtualTestBase {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private Set<String> threadNames;

    @BeforeAll
    static void setupNonNOPLogger() throws FileNotFoundException {
        // use actual log4j logger, and not the NOP loader.
        loadLog4jContext();
    }

    /**
     * Get a set containing all active threads, excluding some threads in thread pools.
     */
    private Set<String> getThreadNames() {
        final long[] threadIds = ManagementFactory.getThreadMXBean().getAllThreadIds();
        final ThreadInfo[] threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(threadIds);

        final Set<String> threadNames = new HashSet<>();

        for (final ThreadInfo info : threadInfo) {
            if (info != null) {
                final String threadName = info.getThreadName();
                if (!threadName.contains("hasher")
                        && !threadName.contains("CacheCleaner-")
                        && !threadName.contains("<virtual-pipeline: lifecycle")
                        && !threadName.contains("ForkJoinPool.commonPool-worker-")
                        && !(threadName.contains("pool-") && threadName.contains("-thread-"))) {
                    threadNames.add(threadName);
                }
            }
        }

        return threadNames;
    }

    @BeforeEach
    void captureInitialThreads() {
        threadNames = getThreadNames();
    }

    @AfterEach
    void captureResultingThreads() throws InterruptedException {
        // Give transient threads some time to gracefully terminate
        MILLISECONDS.sleep(100);

        final Set<String> currentThreadNames = getThreadNames();

        final Set<String> createdThreads = new HashSet<>();
        final Set<String> removedThreads = new HashSet<>();

        for (final String threadName : threadNames) {
            if (!currentThreadNames.contains(threadName)) {
                removedThreads.add(threadName);
            }
        }

        for (final String threadName : currentThreadNames) {
            if (!threadNames.contains(threadName)) {
                createdThreads.add(threadName);
            }
        }

        if (!createdThreads.isEmpty() || !removedThreads.isEmpty()) {

            final StringBuilder sb = new StringBuilder("Threads have changed.\n");

            if (!createdThreads.isEmpty()) {
                sb.append("Created threads:\n");
                for (final String threadName : createdThreads) {
                    sb.append("   - ").append(threadName).append("\n");
                }
            }

            if (!removedThreads.isEmpty()) {
                sb.append("Removed threads:\n");
                for (final String threadName : removedThreads) {
                    sb.append("   - ").append(threadName).append("\n");
                }
            }

            fail(sb.toString());
        }
    }

    /*
     * Test a fresh map
     **/

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Fresh")})
    @DisplayName("A fresh map is mutable")
    void freshMapIsMutable() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        assertEquals(1, fcm.size(), "VirtualMap size is wrong");
        fcm.release();
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Fresh")})
    @DisplayName("A fresh map has both children")
    void freshMapHasBothChildren() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertEquals(2, fcm.getNumberOfChildren(), "VirtualMap size is wrong");
        assertNotNull(fcm.getChild(0), "Unexpected null at index 0");
        assertNotNull(fcm.getChild(1), "Unexpected null at index 1");
        fcm.release();
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Fresh")})
    @DisplayName("A fresh map returns a non-null data source")
    void freshMapHasDataSource() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertNotNull(fcm.getDataSource(), "Unexpected null data source");
        fcm.release();
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Fresh")})
    @DisplayName("The root node of an empty tree has no children")
    void emptyTreeRootHasNoChildren() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        final MerkleInternal root = fcm.getChild(1).asInternal();
        assertEquals(2, root.getNumberOfChildren(), "Unexpected number of children");
        assertNull(root.getChild(0), "Unexpected child of empty root");
        assertNull(root.getChild(1), "Unexpected child of empty root");
        fcm.release();
    }

    @Test
    @Disabled // This test is no longer valid after pipeline changes.
    @Tags({@Tag("VirtualMerkle"), @Tag("Fresh")})
    @DisplayName("A VirtualMap designed to flush waits until previous, non-flushed maps, flush")
    void ifToBeFlushedWaitForPreviousUnflushedMapsToFlush() throws InterruptedException {
        // Save the previous settings
        final VirtualMapSettings originalSettings = VirtualMapSettingsFactory.get();

        // Reconfigure the settings
        final VirtualMapSettings settings = new TestVirtualMapSettings(originalSettings) {
            @Override
            public int getFlushInterval() {
                return 3;
            }
        };
        VirtualMapSettingsFactory.configure(settings);

        try {
            // These first three copies will have "shouldBeFlushed" set to false.
            final VirtualMap<TestKey, TestValue> copy0 = createMap();
            final VirtualMap<TestKey, TestValue> copy1 = copy0.copy();
            final VirtualMap<TestKey, TestValue> copy2 = copy1.copy();
            // This next one has "shouldBeFlushed" set to true. It is using a PauseWhileFlushingDataSource,
            // so when it is asked to flush, it will block trying to do so. But it won't be asked yet.
            final VirtualMap<TestKey, TestValue> copy3 = copy2.copy();
            // These next two will have "shouldBeFlushed" set to false.
            final VirtualMap<TestKey, TestValue> copy4 = copy3.copy();
            final VirtualMap<TestKey, TestValue> copy5 = copy4.copy();
            // Creating the next copy WILL CAUSE THE THREAD TO BLOCK. We need to assert that actually happens.
            final AtomicReference<VirtualMap<TestKey, TestValue>> copy6Ref = new AtomicReference<>();
            final CountDownLatch copy6Created = new CountDownLatch(1);
            new Thread(() -> {
                        // Blocks right here...
                        copy6Ref.set(copy5.copy());
                        copy6Created.countDown();
                    })
                    .start();

            // Now that it is blocked, we will start releasing the older copies. As soon as we release
            // copy3, copy6 should proceed.
            copy0.release();
            copy1.release();
            copy2.release();
            // Try to provoke a bug by giving time between the release of copy2 and the release of copy3.
            // The pipeline should block on copy5.copy() until we release copy3.
            for (int i = 0; i < 100; i++) {
                assertNull(copy6Ref.get(), "Could intermittently fail, but if it does we have a REAL BUG.");
                MILLISECONDS.sleep(1);
            }
            // Now, the moment of truth
            copy3.release();
            try {
                copy6Created.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Failed due to interrupt while waiting for copy6 to be created");
            }
            assertNotNull(copy6Ref.get(), "If copy6 was created, this MUST be non-null or we have a test bug");
            // Release the others
            copy5.release();
            copy6Ref.get().release();
        } finally {
            // Revert to original settings
            VirtualMapSettingsFactory.configure(originalSettings);
        }
    }

    /*
     * Test the fast copy implementation
     **/

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("FastCopy")})
    @DisplayName("Original after copy is immutable")
    void originalAfterCopyIsImmutable() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        final VirtualMap<TestKey, TestValue> copy = fcm.copy();
        assertTrue(fcm.isImmutable(), "Copied VirtualMap should have been immutable");
        assertFalse(copy.isImmutable(), "Most recent VirtualMap should have been mutable");
        fcm.release();
        copy.release();
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("FastCopy")})
    @DisplayName("Cannot copy twice")
    void cannotCopyTwice() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        final VirtualMap<TestKey, TestValue> copy = fcm.copy();
        assertThrows(MutabilityException.class, fcm::copy, "Calling copy twice should have thrown exception");
        fcm.release();
        copy.release();
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("FastCopy")})
    @DisplayName("Cannot copy a released fcm")
    void cannotCopyAReleasedMap() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.release();
        assertThrows(ReferenceCountException.class, fcm::copy, "Calling copy after release should throw");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("FastCopy")})
    @DisplayName("Original is not impacted by changes to modified copy")
    void originalIsUnaffected() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);

        // Perform some combination of add, remove, replace and leaving alone
        final VirtualMap<TestKey, TestValue> copy = fcm.copy();
        copy.replace(A_KEY, AARDVARK);
        copy.remove(C_KEY);
        copy.put(D_KEY, DOG);
        copy.put(E_KEY, EMU);

        assertEquals(APPLE, fcm.get(A_KEY), "Unexpected value");
        assertEquals(BANANA, fcm.get(B_KEY), "Unexpected value");
        assertEquals(CHERRY, fcm.get(C_KEY), "Unexpected value");
        assertEquals(3, fcm.size(), "Unexpected size");

        assertEquals(AARDVARK, copy.get(A_KEY), "Unexpected value");
        assertEquals(BANANA, copy.get(B_KEY), "Unexpected value");
        assertEquals(DOG, copy.get(D_KEY), "Unexpected value");
        assertEquals(EMU, copy.get(E_KEY), "Unexpected value");
        assertEquals(4, copy.size(), "Unexpected size");
        fcm.release();
        copy.release();
    }

    /*
     * Test the map-like implementation
     **/

    @Test
    @DisplayName("Size matches number of items input")
    void sizeMatchesNumberOfItemsInput() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertEquals(0, fcm.size(), "Unexpected size");

        // Add an element
        fcm.put(A_KEY, APPLE);
        assertEquals(1, fcm.size(), "Unexpected size");

        // Add a couple more elements
        fcm.put(B_KEY, BANANA);
        assertEquals(2, fcm.size(), "Unexpected size");
        fcm.put(C_KEY, CHERRY);
        assertEquals(3, fcm.size(), "Unexpected size");

        // replace a couple elements (out of order even!)
        fcm.replace(B_KEY, BEAR);
        fcm.replace(A_KEY, AARDVARK);
        assertEquals(3, fcm.size(), "Unexpected size");

        // Loop and add a million items and make sure the size is matching
        for (int i = 1000; i < 1_001_000; i++) {
            fcm.put(new TestKey(i), new TestValue("value" + i));
        }

        assertEquals(1_000_003, fcm.size(), "Unexpected size");
        fcm.release();
    }

    @Test
    @DisplayName("Is empty when size == 0")
    void isEmptyWhenSizeIsZero() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertTrue(fcm.isEmpty(), "Expected a fresh map to be empty");

        // Add an element
        fcm.put(A_KEY, APPLE);
        assertFalse(fcm.isEmpty(), "Expected a non-empty map");

        // Remove the elements and test that it is back to being empty
        fcm.remove(A_KEY);
        assertTrue(fcm.isEmpty(), "Expected an empty map on deleting the last leaf");
        fcm.release();
    }

    @Test
    @DisplayName("Get of null key throws exception")
    void getOfNullKeyThrowsException() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertThrows(NullPointerException.class, () -> fcm.get(null), "Null keys are not allowed");
        fcm.release();
    }

    @Test
    @DisplayName("Get of missing key returns null")
    void getOfMissingKeyReturnsNull() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);

        assertNull(fcm.get(C_KEY), "Expected no value");
        fcm.release();
    }

    @Test
    @DisplayName("Get of key returns value")
    void getOfKeyReturnsValue() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        assertEquals(APPLE, fcm.get(A_KEY), "Wrong value");
        assertEquals(BANANA, fcm.get(B_KEY), "Wrong value");

        fcm.put(A_KEY, AARDVARK);
        assertEquals(AARDVARK, fcm.get(A_KEY), "Wrong value");
        assertEquals(BANANA, fcm.get(B_KEY), "Wrong value");
        fcm.release();
    }

    @Test
    @DisplayName("Put with null key throws exception")
    void putWithNullKeyThrowsException() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertThrows(NullPointerException.class, () -> fcm.put(null, BANANA), "Null keys are not allowed");

        fcm.release();
    }

    @Test
    @DisplayName("Put with null values are allowed")
    void putWithNullValuesAreAllowed() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, null);
        assertNull(fcm.get(A_KEY), "Expected null");
        fcm.release();
    }

    @Test
    @DisplayName("Multiple keys can have the same value")
    void manyKeysCanHaveTheSameValue() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, null);
        fcm.put(B_KEY, null);
        fcm.put(C_KEY, CUTTLEFISH);
        fcm.put(D_KEY, CUTTLEFISH);

        assertNull(fcm.get(A_KEY), "Expected null");
        assertNull(fcm.get(B_KEY), "Expected null");
        assertEquals(CUTTLEFISH, fcm.get(C_KEY), "Wrong value");
        assertEquals(CUTTLEFISH, fcm.get(D_KEY), "Wrong value");
        assertEquals(4, fcm.size(), "Wrong size");
        fcm.release();
    }

    @Test
    @DisplayName("Put many and get many")
    void putManyAndGetMany() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        for (int i = 0; i < 1000; i++) {
            fcm.put(new TestKey(i), new TestValue("value" + i));
        }

        for (int i = 0; i < 1000; i++) {
            assertEquals(new TestValue("value" + i), fcm.get(new TestKey(i)), "Wrong value");
        }

        fcm.release();
    }

    @Test
    @DisplayName("Replace of non-existent key throws an exception")
    void replaceOfNonExistentKey() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertThrows(IllegalStateException.class, () -> fcm.replace(A_KEY, APPLE), "Expected ISE");

        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        assertThrows(IllegalStateException.class, () -> fcm.replace(C_KEY, CUTTLEFISH), "Expected ISE");
        fcm.release();
    }

    @Test
    @DisplayName("Replace throws exception on null key")
    void replaceThrowsExceptionOnNullKey() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertThrows(NullPointerException.class, () -> fcm.replace(null, BANANA), "Null keys are not allowed");

        fcm.release();
    }

    @Test
    @DisplayName("Replace many and get many")
    void replaceManyAndGetMany() {
        final VirtualMap<TestKey, TestValue> original = createMap();
        for (int i = 0; i < 1000; i++) {
            original.put(new TestKey(i), new TestValue("value" + i));
        }

        final VirtualMap<TestKey, TestValue> fcm = original.copy();
        for (int i = 1000; i < 2000; i++) {
            fcm.replace(new TestKey((i - 1000)), new TestValue("value" + i));
        }

        for (int i = 1000; i < 2000; i++) {
            assertEquals(new TestValue("value" + i), fcm.get(new TestKey((i - 1000))), "Wrong value");
        }

        original.release();
        fcm.release();
    }

    @Test
    @DisplayName("Remove from an empty map")
    void removeEmptyMap() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        final TestValue removed = fcm.remove(A_KEY);
        assertNull(removed, "Expected null");
        fcm.release();
    }

    // TODO test deleting the same key two times in a row.
    // TODO Test that a deleted node's value cannot be subsequently read.

    //    @Test
    //    @Tag(TestTypeTags.FUNCTIONAL)
    //    @Tag(TestComponentTags.FCMAP)
    //    @DisplayName("Remove all leaves by always removing the first leaf")
    //    void removeFirstLeaf() {
    //        var fcm = createMap();
    //        fcm.put(A_KEY, APPLE);
    //        fcm.put(B_KEY, BANANA);
    //        fcm.put(C_KEY, CHERRY);
    //        fcm.put(D_KEY, DATE);
    //        fcm.put(E_KEY, EGGPLANT);
    //        fcm.put(F_KEY, FIG);
    //        fcm.put(G_KEY, GRAPE);
    //
    //        var original = fcm;
    //        fcm = fcm.copy();
    //        CRYPTO.digestTreeSync(original);
    //        original.release();
    //
    //        assertEquals(DATE, fcm.remove(D_KEY));
    //        assertLeafOrder(fcm, A_KEY, E_KEY, C_KEY, F_KEY, B_KEY, G_KEY);
    //        assertEquals(BANANA, fcm.remove(B_KEY));
    //        assertLeafOrder(fcm, A_KEY, E_KEY, C_KEY, F_KEY, G_KEY);
    //        assertEquals(CHERRY, fcm.remove(C_KEY));
    //        assertLeafOrder(fcm, A_KEY, E_KEY, F_KEY, G_KEY);
    //        assertEquals(APPLE, fcm.remove(A_KEY));
    //        assertLeafOrder(fcm, G_KEY, E_KEY, F_KEY);
    //        assertEquals(FIG, fcm.remove(F_KEY));
    //        assertLeafOrder(fcm, G_KEY, E_KEY);
    //        assertEquals(GRAPE, fcm.remove(G_KEY));
    //        assertLeafOrder(fcm, E_KEY);
    //        assertEquals(EGGPLANT, fcm.remove(E_KEY));
    //
    //        // TODO validate hashing works as expected
    //
    //    }

    //    @Test
    //    @Tag(TestTypeTags.FUNCTIONAL)
    //    @Tag(TestComponentTags.FCMAP)
    //    @DisplayName("Remove a middle leaf")
    //    void removeMiddleLeaf() {
    //        var fcm = createMap();
    //        fcm.put(A_KEY, APPLE);
    //        fcm.put(B_KEY, BANANA);
    //        fcm.put(C_KEY, CHERRY);
    //        fcm.put(D_KEY, DATE);
    //        fcm.put(E_KEY, EGGPLANT);
    //        fcm.put(F_KEY, FIG);
    //        fcm.put(G_KEY, GRAPE);
    //
    //        var original = fcm;
    //        fcm = fcm.copy();
    //        CRYPTO.digestTreeSync(original);
    //        original.release();
    //
    //        assertEquals(FIG, fcm.remove(F_KEY));
    //        assertEquals(DATE, fcm.remove(D_KEY));
    //        assertEquals(APPLE, fcm.remove(A_KEY));
    //        assertEquals(BANANA, fcm.remove(B_KEY));
    //        assertEquals(EGGPLANT, fcm.remove(E_KEY));
    //        assertEquals(CHERRY, fcm.remove(C_KEY));
    //        assertEquals(GRAPE, fcm.remove(G_KEY));
    //
    //        // TODO validate hashing works as expected
    //
    //    }

    @Test
    @DisplayName("Add a value and then remove it immediately")
    void removeValueJustAdded() {
        VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        assertEquals(APPLE, fcm.remove(A_KEY), "Wrong value");
        assertEquals(BANANA, fcm.remove(B_KEY), "Wrong value");
        assertEquals(CHERRY, fcm.remove(C_KEY), "Wrong value");
        assertEquals(DATE, fcm.remove(D_KEY), "Wrong value");
        assertEquals(EGGPLANT, fcm.remove(E_KEY), "Wrong value");
        assertEquals(FIG, fcm.remove(F_KEY), "Wrong value");
        assertEquals(GRAPE, fcm.remove(G_KEY), "Wrong value");

        // TODO validate hashing works as expected

        fcm.release();
    }

    @Test
    @DisplayName("Add a value that had just been removed")
    void addValueJustRemoved() {
        VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        assertEquals(APPLE, fcm.remove(A_KEY), "Wrong value");
        assertEquals(BANANA, fcm.remove(B_KEY), "Wrong value");
        assertEquals(CHERRY, fcm.remove(C_KEY), "Wrong value");
        assertEquals(DATE, fcm.remove(D_KEY), "Wrong value");
        assertEquals(EGGPLANT, fcm.remove(E_KEY), "Wrong value");
        assertEquals(FIG, fcm.remove(F_KEY), "Wrong value");
        assertEquals(GRAPE, fcm.remove(G_KEY), "Wrong value");

        fcm.put(D_KEY, DATE);
        // TODO validate hashing works as expected

        fcm.release();
    }

    /*
     * Test various copy and termination scenarios to verify pipeline behavior
     **/

    @Test
    @Tags({@Tag("VirtualMap"), @Tag("Pipeline"), @Tag("VMAP-021")})
    @DisplayName("Database is closed after all copies are released")
    void databaseClosedAfterAllCopiesAreReleased() throws InterruptedException {
        final VirtualMap<TestKey, TestValue> copy0 = createMap();
        final InMemoryDataSource<TestKey, TestValue> ds =
                (InMemoryDataSource<TestKey, TestValue>) copy0.getDataSource();
        final VirtualMap<TestKey, TestValue> copy1 = copy0.copy();
        final VirtualMap<TestKey, TestValue> copy2 = copy1.copy();
        final VirtualMap<TestKey, TestValue> copy3 = copy2.copy();
        final VirtualMap<TestKey, TestValue> copy4 = copy3.copy();

        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy0.release();
        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy1.release();
        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy2.release();
        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy3.release();
        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy4.release();
        assertTrue(copy4.getRoot().getPipeline().awaitTermination(5, SECONDS), "Timed out");
        assertTrue(ds.isClosed(), "Should now be released");
    }

    @Test
    @Tags({@Tag("VirtualMap"), @Tag("Pipeline"), @Tag("VMAP-021")})
    @DisplayName("Database is closed if prematurely terminated")
    void databaseClosedWhenExpresslyTerminated() throws InterruptedException {
        final VirtualMap<TestKey, TestValue> copy0 = createMap();
        final InMemoryDataSource<TestKey, TestValue> ds =
                (InMemoryDataSource<TestKey, TestValue>) copy0.getDataSource();
        final VirtualMap<TestKey, TestValue> copy1 = copy0.copy();
        final VirtualMap<TestKey, TestValue> copy2 = copy1.copy();
        final VirtualMap<TestKey, TestValue> copy3 = copy2.copy();
        final VirtualMap<TestKey, TestValue> copy4 = copy3.copy();

        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy0.release();
        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy1.release();
        assertFalse(ds.isClosed(), "Should not be closed yet");
        copy2.getRoot().getPipeline().terminate();
        assertTrue(copy2.getRoot().getPipeline().awaitTermination(5, SECONDS), "Timed out");
        assertTrue(ds.isClosed(), "Should now be released");
    }

    /*
     * Test iteration and hashing
     **/

    //    @Test
    //    @Tag(TestTypeTags.FUNCTIONAL)
    //    @Tag(TestComponentTags.FCMAP)
    //    @DisplayName("Newly created maps have null hashes for everything")
    //    void nullHashesOnNewMap() throws ExecutionException, InterruptedException {
    //        var fcm = createMap();
    //        fcm.put(A_KEY, APPLE);
    //        fcm.put(B_KEY, BANANA);
    //        fcm.put(C_KEY, CHERRY);
    //        fcm.put(D_KEY, DATE);
    //        fcm.put(E_KEY, EGGPLANT);
    //        fcm.put(F_KEY, FIG);
    //        fcm.put(G_KEY, GRAPE);
    //
    // TODO Cannot iterate until after hashing, which invalidates the test
    //        var completed = fcm;
    //        fcm = fcm.copy();
    //        completed.hash().get();
    //        final var breadthItr = new MerkleBreadthFirstIterator<MerkleNode, MerkleNode>(completed);
    //        while (breadthItr.hasNext()) {
    //            assertNull(breadthItr.next().getHash());
    //        }
    //    }

    @Test
    @DisplayName("Hashed maps have non-null hashes on everything")
    void nonNullHashesOnHashedMap() throws ExecutionException, InterruptedException {
        VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        final VirtualMap<TestKey, TestValue> completed = fcm;
        fcm = fcm.copy();
        MerkleCryptoFactory.getInstance().digestTreeSync(completed);

        final Iterator<MerkleNode> breadthItr = completed.treeIterator().setOrder(BREADTH_FIRST);
        while (breadthItr.hasNext()) {
            assertNotNull(breadthItr.next().getHash(), "Expected a value");
        }

        completed.release();
        fcm.release();
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Million sized hashed maps have non-null hashes on everything")
    void millionNonNullHashesOnHashedMap() throws ExecutionException, InterruptedException {
        VirtualMap<TestKey, TestValue> fcm = createMap();
        for (int i = 0; i < 1_000_000; i++) {
            fcm.put(new TestKey(i), new TestValue("" + i));
        }

        final VirtualMap<TestKey, TestValue> completed = fcm;
        fcm = fcm.copy();

        final Hash firstHash = MerkleCryptoFactory.getInstance().digestTreeSync(completed);
        final Iterator<MerkleNode> breadthItr = completed.treeIterator().setOrder(BREADTH_FIRST);
        while (breadthItr.hasNext()) {
            assertNotNull(breadthItr.next().getHash(), "Expected a value");
        }

        final Random rand = new Random(1234);
        for (int i = 0; i < 10_000; i++) {
            final int index = rand.nextInt(1_000_000);
            final int value = 1_000_000 + rand.nextInt(1_000_000);
            fcm.put(new TestKey(index), new TestValue("" + value));
        }

        final VirtualMap second = fcm;
        fcm = copyAndRelease(fcm);
        final Hash secondHash = MerkleCryptoFactory.getInstance().digestTreeSync(second);
        assertNotSame(firstHash, secondHash, "Wrong value");

        fcm.release();
        completed.release();
    }

    @Test
    @DisplayName("GetForModify should not mutate old copies")
    void checkGetForModifyMutation() throws InterruptedException {
        final VirtualMap<TestKey, TestValue> vm = createMap();
        vm.put(A_KEY, APPLE);
        final TestValue value = vm.getForModify(A_KEY);
        final VirtualMap<TestKey, TestValue> vm2 = vm.copy();

        final TestValue value2 = vm2.getForModify(A_KEY);
        value2.setValue("Mutant2");

        final TestValue value3 = vm.get(A_KEY);

        assertEquals("Mutant2", value2.value());
        assertEquals("Apple", value3.value());
        assertEquals("Apple", value.value());
    }

    @Test(/* no exception expected */ )
    @DisplayName("Partly dirty maps have missing hashes only on dirty leaves and parents")
    @Disabled("Need to work out how to test ths properly")
    void nullHashesOnDirtyNodes() throws ExecutionException, InterruptedException {
        VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        fcm = copyAndRelease(fcm);

        // Both of these are on different parents, but the same grandparent.
        fcm.replace(D_KEY, DOG);
        fcm.put(B_KEY, BEAR);

        // This hash iterator should visit MapState, B, <internal>, D, <internal>, <internal (root)>, fcm
        // TODO gotta figure out how to test
        //        final var hashItr = new MerkleHashIterator(fcm);
        //        hashItr.next();
        //        assertEquals(new VFCLeafNode<>(B_KEY, BEAR), getRecordFromNode((MerkleLeaf) hashItr.next()));
        //        hashItr.next();
        //        assertEquals(new VFCLeafNode<>(D_KEY, DOG), getRecordFromNode((MerkleLeaf) hashItr.next()));
        //        hashItr.next();
        //        hashItr.next();
        //        assertEquals(fcm, hashItr.next());
        //        assertFalse(hashItr.hasNext());

        fcm.release();
    }

    @Test
    void testAsyncHashing() throws ExecutionException, InterruptedException {
        VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);
        fcm.put(D_KEY, DATE);
        fcm.put(E_KEY, EGGPLANT);
        fcm.put(F_KEY, FIG);
        fcm.put(G_KEY, GRAPE);

        VirtualMap<TestKey, TestValue> completed = fcm;
        fcm = fcm.copy();
        final Hash expectedHash = completed.getHash();

        VirtualMap<TestKey, TestValue> fcm2 = createMap();
        fcm2.put(A_KEY, APPLE);
        fcm2.put(B_KEY, BANANA);
        fcm2.put(C_KEY, CHERRY);
        fcm2.put(D_KEY, DATE);
        fcm2.put(E_KEY, EGGPLANT);
        fcm2.put(F_KEY, FIG);
        fcm2.put(G_KEY, GRAPE);

        completed.release();
        completed = fcm2;
        fcm2 = fcm2.copy();
        final Hash actualHash = completed.getHash();
        assertEquals(expectedHash, actualHash, "Wrong value");

        fcm.release();
        fcm2.release();
        completed.release();
    }

    @Test
    @DisplayName("Check expected warnings about VirtualMap size near/at capacity")
    void testVirtualMapSizeSettings() throws ExecutionException, InterruptedException, IOException {
        // Save the previous settings
        final VirtualMapSettings originalSettings = VirtualMapSettingsFactory.get();

        // Reconfigure the settings
        final VirtualMapSettings settings = new TestVirtualMapSettings(originalSettings) {
            @Override
            public double getPercentCleanerThreads() {
                return 10.0;
            }

            @Override
            public int getNumCleanerThreads() {
                return 2;
            }

            @Override
            public long getMaximumVirtualMapSize() {
                return 6L;
            }

            @Override
            public long getVirtualMapWarningThreshold() {
                return 4L;
            }

            @Override
            public long getVirtualMapWarningInterval() {
                return 2L;
            }
        };

        try {
            VirtualMapSettingsFactory.configure(settings);
            VirtualMap<TestKey, TestValue> fcm = createMap();
            fcm.put(A_KEY, APPLE);
            fcm.put(B_KEY, BANANA); // should trigger first warning
            fcm.put(C_KEY, CHERRY);
            fcm.put(D_KEY, DATE); // should trigger second warning
            fcm.remove(D_KEY);
            fcm.remove(C_KEY);
            fcm = fcm.copy(); // should retain previous maxSizeReachedTriggeringWarning
            fcm.put(C_KEY, CHERRY);
            fcm.put(D_KEY, DATE); // should not re-trigger second warning here.
            fcm.put(E_KEY, EGGPLANT);
            fcm.put(F_KEY, FIG); // should trigger final warning (no space left)
            final VirtualMap<TestKey, TestValue> finalFcm = fcm;
            final Exception exception = assertThrows(
                    IllegalStateException.class,
                    () -> {
                        finalFcm.put(G_KEY, GRAPE);
                    },
                    "Expected to catch IllegateStateException because the VirtualMap is full!");
            assertEquals("Virtual Map has no more space", exception.getMessage(), "Unexpected exception message.");

            final String logContents = Files.readString(Path.of("swirlds.log"));
            assertTrue(
                    containsRegex(
                            "^.*WARN  VIRTUAL_MERKLE_STATS <[a-zA-Z0-9\\-\\s]+> VirtualRootNode: Virtual Map only has"
                                    + " room for 4 additional entries$",
                            logContents),
                    "Based on the virtualMapWarningThreshold setting");
            assertTrue(
                    containsRegex(
                            "^.*WARN  VIRTUAL_MERKLE_STATS <[a-zA-Z0-9\\-\\s]+> VirtualRootNode: Virtual Map only has"
                                    + " room for 2 additional entries$",
                            logContents),
                    "Based on the virtualMapWarningThreshold setting");
            assertTrue(
                    containsRegex(
                            "^.*WARN  VIRTUAL_MERKLE_STATS <[a-zA-Z0-9\\-\\s]+> VirtualRootNode: Virtual Map is now "
                                    + "full!$",
                            logContents),
                    "When remaining capacity is only 1");
            // make sure each line only appears once - so (due to leading/trailing characters) - should be 3 log lines
            assertEquals(
                    3,
                    countRegex("WARN  VIRTUAL_MERKLE_STATS <(main|Test worker)>", logContents),
                    "Unexpected number of VIRTUAL_MERKLE_STATS warnings");
        } finally {
            // Revert to original settings
            VirtualMapSettingsFactory.configure(originalSettings);
        }
    }

    /**
     * This test validates that for the basic tree below, the routes are set correctly. When the tests are moved to the
     * swirlds-test module, we should use a MerkleMap and insert one million elements, and insert the same elements into
     * a {@link VirtualMap}. Then, we iterate over the routes of both maps and their routes should match.
     *
     * <pre>
     *                      VirtualMap
     *                         []
     *                     /       \
     *                    /         \
     *         VirtualMapState      Root
     *                 [0]           [1]
     *                             /     \
     *                            /       \
     *                        Internal     B
     *                        [1, 0]     [1, 1]
     *                        /   \
     *                       /     \
     *                      A       C
     *               [1, 0, 0]    [1, 0, 1]
     * </pre>
     */
    @Test
    void routesSetForBasicTree() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        fcm.put(A_KEY, APPLE);
        fcm.put(B_KEY, BANANA);
        fcm.put(C_KEY, CHERRY);

        final List<MerkleNode> nodes = new ArrayList<>();
        fcm.forEachNode(node -> {
            nodes.add(node);
        });

        assertEquals(MerkleRouteFactory.buildRoute(0), nodes.get(0).getRoute(), "VirtualMapState");
        assertEquals(MerkleRouteFactory.buildRoute(1, 0, 0), nodes.get(1).getRoute(), "VirtualLeafNode A");
        assertEquals(MerkleRouteFactory.buildRoute(1, 0, 1), nodes.get(2).getRoute(), "VirtualLeafNode C");
        assertEquals(MerkleRouteFactory.buildRoute(1, 0), nodes.get(3).getRoute(), "VirtualInternalNode");
        assertEquals(MerkleRouteFactory.buildRoute(1, 1), nodes.get(4).getRoute(), "VirtualLeafNode B");
        assertEquals(MerkleRouteFactory.buildRoute(1), nodes.get(5).getRoute(), "VirtualInternalNode Root");
        assertEquals(MerkleRouteFactory.buildRoute(), nodes.get(6).getRoute(), "VirtualMap");
    }

    @Test
    void testMapConstructedWithDefaultConstructorIsInvalid() {
        VirtualMap<TestKey, TestValue> subject = new VirtualMap<>();
        assertFalse(subject.isValid());
    }

    @Test
    void testFreshMapIsValid() {
        final VirtualMap<TestKey, TestValue> fcm = createMap();
        assertTrue(fcm.isValid());
        fcm.release();
    }

    /**
     * Make a copy of a map and release the original.
     */
    private VirtualMap<TestKey, TestValue> copyAndRelease(final VirtualMap<TestKey, TestValue> original) {
        final VirtualMap<TestKey, TestValue> copy = original.copy();
        original.release();
        return copy;
    }

    /*
     * Test statistics on a fresh map
     **/

    /**
     * Bug #4233 was caused by an NPE when flushing a copy that had been detached for the sake of state saving. This
     * happened because the detach for state saving does not result in the detached state having a data source.
     */
    @Test
    void canFlushDetachedStateForStateSaving() throws InterruptedException {
        final VirtualMap<TestKey, TestValue> map0 = createMap();
        map0.put(A_KEY, APPLE);
        map0.put(B_KEY, BANANA);
        map0.put(C_KEY, CHERRY);
        map0.put(D_KEY, DATE);

        final VirtualMap<TestKey, TestValue> map1 = map0.copy();
        map1.put(E_KEY, EGGPLANT);
        map1.put(F_KEY, FIG);
        map1.put(G_KEY, GRAPE);

        final VirtualMap<TestKey, TestValue> map2 = map1.copy();

        assertNotNull(map1.getRoot().getHash(), "Hash should have been produced for map1");

        // Detach, and then make another copy which should cause it to flush.
        map1.getRoot().enableFlush();
        map1.getRoot().detach(Path.of("tmp"));
        map0.release();

        final CountDownLatch finishedFlushing = new CountDownLatch(1);
        final Thread th = new Thread(() -> {
            try {
                map1.getRoot().waitUntilFlushed();
                finishedFlushing.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Timed out waiting for flush");
            }
        });
        th.start();

        try {
            if (!finishedFlushing.await(1, SECONDS)) {
                th.interrupt();
                fail("Timed out, which happens if the test fails or the test has a bug but never if it passes");
            }
        } finally {
            map1.release();
            map2.release();
        }
    }

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Tests vMapFlushes metric")
    void testFlushCount() throws IOException, InterruptedException {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        final MetricKeyRegistry registry = mock(MetricKeyRegistry.class);
        when(registry.register(any(), any(), any())).thenReturn(true);
        final Metrics metrics = new DefaultMetrics(
                null, registry, mock(ScheduledExecutorService.class), new DefaultMetricsFactory(), metricsConfig);

        VirtualMap<TestKey, TestValue> map0 = createMap();
        map0.registerMetrics(metrics);

        int flushCount = 0;
        final int totalCount = 1000;
        for (int i = 0; i < totalCount; i++) {
            VirtualMap<TestKey, TestValue> map1 = map0.copy();
            map0.release();
            map0 = map1;

            VirtualRootNode<TestKey, TestValue> root = map0.getRight();
            // Make sure at least some maps need to be flushed, including the last one
            if ((i % 57 == 0) || (i == totalCount - 1)) {
                root.enableFlush();
            }

            if (root.shouldBeFlushed()) {
                flushCount++;
            }
        }

        // Don't release the last map yet, as it would terminate the pipeline. Make a copy first,
        // release the map, then wait for the root to be flushed, then release the copy
        VirtualRootNode<TestKey, TestValue> lastRoot = map0.getRight();
        VirtualMap<TestKey, TestValue> map1 = map0.copy();
        map0.release();
        lastRoot.waitUntilFlushed();
        map1.release();

        // createMap() creates a map labelled "Test"
        Metric metric = metrics.getMetric(VirtualMapStatistics.STAT_CATEGORY, "vMapFlushes_Test");
        assertNotNull(metric);
        if (!(metric instanceof Counter counterMetric)) {
            throw new AssertionError("vMapFlushes metric is not a counter");
        }
        assertEquals(flushCount, counterMetric.get());
    }

    /*
     * Test serialization and deserialization
     **/

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("A copied map is serializable and then deserializable")
    void testExternalSerializationAndDeserialization() throws IOException {
        final VirtualMap<TestKey, TestValue> map0 = createMap();
        map0.getState().setLabel("serializationTest");
        assertEquals("serializationTest", map0.getLabel());
        map0.put(A_KEY, APPLE);
        map0.put(B_KEY, BANANA);
        map0.put(C_KEY, CHERRY);
        map0.put(D_KEY, DATE);
        map0.put(E_KEY, EGGPLANT);
        map0.put(F_KEY, FIG);
        map0.put(G_KEY, GRAPE);

        final VirtualMap<TestKey, TestValue> map1 = map0.copy(); // this should make map0 immutable
        assertEquals("serializationTest", map1.getLabel());
        assertNotNull(map0.getRoot().getHash(), "Hash should have been produced for map0");
        assertTrue(map0.isImmutable(), "Copied VirtualMap should have been immutable");
        assertVirtualMapsEqual(map0, map1);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        // serialize the existing maps
        map0.serialize(out, testDirectory);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final VirtualMap<TestKey, TestValue> map2 = createMap();
        // read the serialized map back into map2
        // Note to Jasper/Richard: The call to deserializeException below fails - but somewhat unexpectedly!
        // Did I not set up the serialiaztion/deserialization correctly?
        // currently throws IOException here.
        map2.deserialize(in, testDirectory, VirtualMap.ClassVersion.MERKLE_SERIALIZATION_CLEANUP);
        assertEquals("serializationTest", map2.getLabel());
        assertVirtualMapsEqual(map0, map2);

        // release the maps and clean up the temporary directory
        map0.release();
        map1.release();
        map2.release();
        deleteDirectory(testDirectory);
    }

    /*
     * Test some bigger scenarios
     **/

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VMAP-019")})
    @DisplayName("Insert one million elements with same key but different value")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void insertRemoveAndModifyOneMillion() throws InterruptedException {
        final int changesPerBatch = 15_432; // Some unexpected size just to be crazy
        final int max = 1_000_000;
        VirtualMap<TestKey, TestValue> map = createMap("insertRemoveAndModifyOneMillion");
        try {
            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i), new TestValue(i));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i), map.get(new TestKey(i)), "Expected same");
            }

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.remove(new TestKey(i));
            }

            assertTrue(map.isEmpty(), "Map should be empty");

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i + max), new TestValue(i + max));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i + max), map.get(new TestKey(i + max)), "Expected same");
                assertNull(map.get(new TestKey(i)), "The old value should not exist anymore");
            }
        } finally {
            map.release();
        }
    }

    // based heavily on VirtualMapGroup::validateCopy(), but modified to just compare two VirtualMaps, instead of
    // also taking in a "ref" Math of values to compare each map to.
    private void assertVirtualMapsEqual(
            final VirtualMap<TestKey, TestValue> mapA, final VirtualMap<TestKey, TestValue> mapB) {
        final boolean immutable = mapA.isImmutable();

        if (mapA.size() != mapB.size()) {
            throw new RuntimeException("size does not match"); // Add a breakpoint here
        }

        final Map<MerkleRoute, Hash> hashes = new HashMap<>();

        mapA.forEachNode((final MerkleNode node) -> {
            if (immutable) {
                hashes.put(node.getRoute(), node.getHash());
            }

            if (node instanceof VirtualLeafNode) {
                final VirtualLeafNode<TestKey, TestValue> leaf = node.cast();

                final TestKey key = leaf.getKey();
                final TestValue value = leaf.getValue();

                if (!Objects.equals(mapB.get(key), value)) {
                    throw new RuntimeException("values do not match for key " + key + ": mapA = " + value + ", mapB ="
                            + mapB.get(key) + "."); // Add a breakpoint here
                }
            }
        });

        mapB.forEachNode((final MerkleNode node) -> {
            if (immutable) {
                if (!hashes.containsKey(node.getRoute())) {
                    throw new RuntimeException("topology differs between trees"); // Add a breakpoint here
                }
                if (!Objects.equals(hashes.get(node.getRoute()), node.getHash())) {
                    throw new RuntimeException("hashes differ between trees"); // Add a breakpoint here
                }
            }

            if (node instanceof VirtualLeafNode) {
                final VirtualLeafNode<TestKey, TestValue> leaf = node.cast();

                final TestKey key = leaf.getKey();
                final TestValue value = leaf.getValue();

                if (!Objects.equals(mapA.get(key), value)) {
                    throw new RuntimeException("values do not match for key " + key + ": mapB = " + value + ", mapA ="
                            + mapA.get(key) + "."); // Add a breakpoint here
                }
            }
        });
    }

    private boolean containsRegex(final String regex, final String haystack) {
        final Pattern exp = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher m = exp.matcher(haystack);

        return m.find();
    }

    private int countRegex(final String regex, final String haystack) {
        final Pattern exp = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher m = exp.matcher(haystack);

        int hits = 0;
        while (!m.hitEnd()) {
            if (m.find()) {
                hits++;
            }
        }

        return hits;
    }
}
