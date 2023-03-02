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

package com.swirlds.virtualmap.internal.hash;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.crypto.Hash;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualTestBase;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the {@link VirtualHasher}.
 */
class VirtualHasherTest extends VirtualHasherTestBase {

    /**
     * If the stream is null, an NPE will be raised.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Null stream produces NPE")
    void nullStreamProducesNPE() {
        final TestDataSource ds = new TestDataSource(1, 2);
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        assertThrows(
                NullPointerException.class,
                () -> hasher.hash(ds::getLeaf, ds::getInternal, null, 1, 2),
                "Call should have produced an NPE");
    }

    /**
     * If the stream is empty, a null hash is returned.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Empty stream results in a null hash")
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "RedundantOperationOnEmptyContainer"})
    void emptyStreamProducesNull() {
        final TestDataSource ds = new TestDataSource(1, 2);
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        final List<VirtualLeafRecord<TestKey, TestValue>> leaves = new ArrayList<>();
        assertNull(
                hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), 1, 2),
                "Call should have returned a null hash");
    }

    /**
     * If either the firstLeafPath or lastLeafPath is &lt; 1, then a null hash is returned.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Invalid leaf paths returns null for hash")
    void invalidFirstOrLastLeafPathProducesNull() {
        final TestDataSource ds = new TestDataSource(Path.INVALID_PATH, Path.INVALID_PATH);
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        final List<VirtualLeafRecord<TestKey, TestValue>> leaves = new ArrayList<>();
        leaves.add(appleLeaf(VirtualTestBase.A_PATH));
        assertNull(
                hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), Path.INVALID_PATH, 2),
                "Call should have produced null");
        assertNull(
                hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), 1, Path.INVALID_PATH),
                "Call should have produced null");
        assertNull(
                hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), 0, 2), "Call should have produced null");
        assertNull(
                hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), 1, 0), "Call should have produced null");
    }

    /**
     * Test a large variety of different tree shapes and sizes and a variety of different dirty leaves
     * in those trees.
     *
     * @param firstLeafPath
     * 		The first leaf path.
     * @param lastLeafPath
     * 		The last leaf path.
     * @param dirtyPaths
     * 		The leaf paths that are dirty in this tree.
     */
    @ParameterizedTest
    @MethodSource("hashingPermutations")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Test various dirty nodes in a tree")
    void hashingPermutations(final long firstLeafPath, final long lastLeafPath, final List<Long> dirtyPaths) {
        final TestDataSource ds = new TestDataSource(firstLeafPath, lastLeafPath);
        final HashingListener listener = new HashingListener();
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        final Hash expected = hashTree(ds);
        final List<VirtualLeafRecord<TestKey, TestValue>> leaves = invalidateNodes(ds, dirtyPaths.stream());
        final Hash rootHash =
                hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), firstLeafPath, lastLeafPath, listener);
        assertEquals(expected, rootHash, "Hash value does not match expected");

        // Make sure the saver saw each dirty leaf exactly once.
        final List<VirtualLeafRecord<TestKey, TestValue>> sortedLeaves = listener.sortedLeaves();
        assertEquals(dirtyPaths.size(), sortedLeaves.size(), "Unexpected size");
        for (int i = 0; i < sortedLeaves.size(); i++) {
            assertEquals(dirtyPaths.get(i), sortedLeaves.get(i).getPath(), "Paths did not match");
        }

        // Make sure the saver saw each dirty internal exactly once.
        final Set<Long> savedInternals = listener.unsortedInternals().stream()
                .map(VirtualRecord::getPath)
                .collect(Collectors.toSet());
        final Set<VirtualInternalRecord> seenInternals = new HashSet<>();
        for (final Long dirtyPath : dirtyPaths) {
            VirtualInternalRecord internal = ds.getInternal(Path.getParentPath(dirtyPath));
            while (internal != null && internal.getPath() > 0) {
                assertTrue(savedInternals.contains(internal.getPath()), "Expected true");
                seenInternals.add(internal);
                internal = ds.getInternal(Path.getParentPath(internal.getPath()));
            }
        }
        assertTrue(savedInternals.contains(0L), "Expected true");
        assertEquals(savedInternals.size(), seenInternals.size() + 1, "Expected equals");
        assertCallsAreBalanced(listener);
        assertRecordsInRankAreAscendingPathOrder(listener);
    }

    /**
     * Generate permutations of trees for testing hashing.
     *
     * @return
     * 		The stream of args.
     */
    private static Stream<Arguments> hashingPermutations() {
        // Generate every permutation for the number of leaves 1-4. 1 leaf, 2 leaves, 3 leaves, 4 leaves,
        // and every permutation of those with leaves dirty and clean.
        final List<Arguments> args = new ArrayList<>();
        // 1 leaf
        args.add(Arguments.of(1L, 1L, List.of(1L)));
        // 2 leaves
        args.add(Arguments.of(1L, 2L, List.of(1L)));
        args.add(Arguments.of(1L, 2L, List.of(2L)));
        args.add(Arguments.of(1L, 2L, List.of(1L, 2L)));
        // 3 leaves
        args.add(Arguments.of(2L, 4L, List.of(2L)));
        args.add(Arguments.of(2L, 4L, List.of(3L)));
        args.add(Arguments.of(2L, 4L, List.of(2L, 3L)));
        args.add(Arguments.of(2L, 4L, List.of(4L)));
        args.add(Arguments.of(2L, 4L, List.of(2L, 4L)));
        args.add(Arguments.of(2L, 4L, List.of(3L, 4L)));
        args.add(Arguments.of(2L, 4L, List.of(2L, 3L, 4L)));
        // 4 leaves
        args.add(Arguments.of(3L, 6L, List.of(3L)));
        args.add(Arguments.of(3L, 6L, List.of(4L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 4L)));
        args.add(Arguments.of(3L, 6L, List.of(5L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 5L)));
        args.add(Arguments.of(3L, 6L, List.of(4L, 5L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 4L, 5L)));
        args.add(Arguments.of(3L, 6L, List.of(6L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 6L)));
        args.add(Arguments.of(3L, 6L, List.of(4L, 6L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 4L, 6L)));
        args.add(Arguments.of(3L, 6L, List.of(5L, 6L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 5L, 6L)));
        args.add(Arguments.of(3L, 6L, List.of(4L, 5L, 6L)));
        args.add(Arguments.of(3L, 6L, List.of(3L, 4L, 5L, 6L)));

        // Generate every permutation of which leaves are clean and dirty. This method assumes
        // that the set of leaves is path 6-12 only.
        //
        // Imagine a bitset where each bit position represents whether a given leaf
        // should be clean or dirty, and we generate an Arguments instance for each
        // permutation of that bitset.
        for (int i = 1; i < Math.pow(2, 7); i++) {
            final List<Long> dirtyIndexes = new ArrayList<>();
            for (long j = 6, bits = i; j < 13; j++, bits >>= 1) {
                if ((bits & 0x1) == 1) {
                    dirtyIndexes.add(j);
                }
            }
            args.add(Arguments.of(6L, 12L, dirtyIndexes));
        }

        // Generate a useful set of scenarios for the medium tree case, where the "medium" tree is still
        // quite small (32 or 53 leaves), but still large enough that we cannot test every permutation.
        // We will test edge cases (1 dirty, all 53 permutations; all dirty; 1 dirty at start and end; etc.).
        // We will also test a few thousand pseudo-random varieties for a little fuzz testing.
        //
        // Setup tests for a 32 leaf tree -- such that all leaves form a single rank in the tree.
        // First test all permutations of 1 dirty leaf
        long firstLeafPath = 31;
        long lastLeafPath = 62;
        for (long i = firstLeafPath; i <= lastLeafPath; i++) {
            args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(i)));
        }
        // Then test the first and last both being dirty
        args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(firstLeafPath, lastLeafPath)));
        // Then test some random assortment
        args.addAll(randomDirtyLeaves(1000, firstLeafPath, lastLeafPath));

        // Setup tests for the 32 leaf tree -- such that leaves spread across two ranks,
        // with almost all leaves on the lower rank and only 2 leaves on the bottom rank.
        // All 32 permutations of a single leaf being dirty
        firstLeafPath = 32;
        lastLeafPath = 64;
        for (long i = firstLeafPath; i <= lastLeafPath; i++) {
            args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(i)));
        }
        // Then test the first and last both being dirty
        args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(firstLeafPath, lastLeafPath)));
        // Then test some random assortment
        args.addAll(randomDirtyLeaves(2000, firstLeafPath, lastLeafPath));

        // Setup tests for the 53 leaf tree -- such that leaves spread across two ranks.
        // All 53 permutations of a single leaf being dirty
        firstLeafPath = 52;
        lastLeafPath = 104;
        for (long i = firstLeafPath; i <= lastLeafPath; i++) {
            args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(i)));
        }
        // Then test the first and last both being dirty
        args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(firstLeafPath, lastLeafPath)));
        // Then test some random assortment
        args.addAll(randomDirtyLeaves(1000, firstLeafPath, lastLeafPath));

        // Generate a useful sample of tree configurations between 4 and 6 ranks deep, and a random sampling
        // of dirty nodes within those trees.
        for (firstLeafPath = 7L, lastLeafPath = 14L; firstLeafPath < 31L; firstLeafPath++, lastLeafPath += 2) {
            for (long i = firstLeafPath; i <= lastLeafPath; i++) {
                args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(i)));
            }
            args.add(Arguments.of(firstLeafPath, lastLeafPath, List.of(firstLeafPath, lastLeafPath)));
            args.addAll(randomDirtyLeaves(100, firstLeafPath, lastLeafPath));
        }

        return args.stream();
    }

    /**
     * Given our "canonical" 53-leaf dirty list (as used during the design phase when diagramming),
     * run the test repeatedly to make sure it always works. Early on I found some threading bugs
     * this way, and I want to make sure that given the same input we get the same result every time.
     * <p>
     * If this test fails, then there is a legitimate issue. If it passes, it only means that we
     * *may* not have a problem. So if this is intermittent, then we have a problem.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Test the same situation over and over and over")
    void repeatedTest() {
        final TestDataSource ds = new TestDataSource(52L, 104L);
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        final Hash expected = hashTree(ds);
        final List<Long> dirtyLeafPaths = List.of(
                53L, 56L, 59L, 63L, 66L, 72L, 76L, 77L, 80L, 81L, 82L, 83L, 85L, 87L, 88L, 94L, 96L, 100L, 104L);

        // Iterate a thousand times, doing the same thing. If there are race conditions among the hashing threads,
        // this will *likely* find it.
        for (int i = 0; i < 1000; i++) {
            final List<VirtualLeafRecord<TestKey, TestValue>> leaves = invalidateNodes(ds, dirtyLeafPaths.stream());
            final Hash rootHash = hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), 52L, 104L);
            assertEquals(expected, rootHash, "Expected equals");
        }
    }

    /**
     * Test that the various callbacks on the listener are called the expected number of times.
     * For this test, I'm using our "canonical" example. I wish I could post the image directly
     * in these javadocs. Instead, look for the images in the design docs.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Verify methods on the listener are called at the right frequency")
    void listenerCallCounts() {
        final TestDataSource ds = new TestDataSource(52L, 104L);
        final HashingListener listener = new HashingListener();
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        hashTree(ds);
        final List<Long> dirtyLeafPaths = List.of(
                53L, 56L, 59L, 63L, 66L, 72L, 76L, 77L, 80L, 81L, 82L, 83L, 85L, 87L, 88L, 94L, 96L, 100L, 104L);

        final List<VirtualLeafRecord<TestKey, TestValue>> leaves = invalidateNodes(ds, dirtyLeafPaths.stream());
        hasher.hash(ds::getLeaf, ds::getInternal, leaves.iterator(), 52L, 104L, listener);

        // Check the different callbacks were called the correct number of times
        assertEquals(1, listener.onHashingStartedCallCount, "Unexpected count");
        assertEquals(3, listener.onBatchStartedCallCount, "Unexpected count");
        assertEquals(12, listener.onRankStartedCallCount, "Unexpected count");
        assertEquals(42, listener.onInternalHashedCallCount, "Unexpected count");
        assertEquals(19, listener.onLeafHashedCallCount, "Unexpected count");
        assertEquals(12, listener.onRankCompletedCallCount, "Unexpected count");
        assertEquals(3, listener.onBatchCompletedCallCount, "Unexpected count");
        assertEquals(1, listener.onHashingCompletedCallCount, "Unexpected count");

        // Validate the calls were all balanced
        assertCallsAreBalanced(listener);
    }

    /**
     * We found a bug while doing large reconnect tests where the VirtualHasher was asking for
     * {@link VirtualInternalRecord}s before they had been written (#4251). In reality, the
     * hasher never should have been asking for those records in the first place (this was a
     * needless performance problem). This test covers that specific case.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Verify the hasher does not ask for internal records it will recreate")
    void hasherDoesNotAskForInternalsItWillRecreate() {
        final HashingListener listener = new HashingListener();
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();

        // We will simulate growing the tree from 53 leaves to 106 leaves (doubling the size) and providing
        // all new leaves. This will guarantee that some internal nodes live at the paths that leaves used
        // to. We'll then hash in several batches. If there are no exceptions, then we're OK.
        final long firstLeafPath = 105L;
        final long lastLeafPath = 211L;
        final Iterator<VirtualLeafRecord<TestKey, TestValue>> dirtyLeaves = LongStream.range(
                        firstLeafPath, lastLeafPath)
                .mapToObj(path -> new VirtualLeafRecord<>(path, null, new TestKey(path), new TestValue(path)))
                .iterator();

        final LongFunction<VirtualLeafRecord<TestKey, TestValue>> leafReader = path -> {
            throw new AssertionError("Leaves should not be queried");
        };

        final LongFunction<VirtualInternalRecord> internalReader = path -> {
            throw new AssertionError("Internals should not be queried");
        };

        assertDoesNotThrow(
                () -> {
                    hasher.hash(leafReader, internalReader, dirtyLeaves, firstLeafPath, lastLeafPath, listener);
                },
                "Hashing should not throw an exception");
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static void assertCallsAreBalanced(final HashingListener listener) {
        // Check the call order was correct. Something like:
        // {[<LLLLLL><IIII><II>][<LLLLLL><IIII><II>]}
        final Deque<Character> tokenStack = new ArrayDeque<>();
        final String tokens = listener.callHistory.toString();
        for (int i = 0; i < tokens.length(); i++) {
            final char token = tokens.charAt(i);
            switch (token) {
                case HashingListener.ON_HASHING_STARTED_SYMBOL:
                case HashingListener.ON_BATCH_STARTED_SYMBOL:
                case HashingListener.ON_RANK_STARTED_SYMBOL:
                    tokenStack.push(token);
                    break;
                case HashingListener.ON_INTERNAL_SYMBOL:
                case HashingListener.ON_LEAF_SYMBOL:
                    break;
                case HashingListener.ON_RANK_COMPLETED_SYMBOL:
                    assertEquals(
                            HashingListener.ON_RANK_STARTED_SYMBOL,
                            tokenStack.pop(),
                            "Unbalanced calls: expected onRankCompleted to be called");
                    break;
                case HashingListener.ON_BATCH_COMPLETED_SYMBOL:
                    assertEquals(
                            HashingListener.ON_BATCH_STARTED_SYMBOL,
                            tokenStack.pop(),
                            "Unbalanced calls: expected onBatchCompleted to be called");
                    break;
                case HashingListener.ON_HASHING_COMPLETED_SYMBOL:
                    assertEquals(
                            HashingListener.ON_HASHING_STARTED_SYMBOL,
                            tokenStack.pop(),
                            "Unbalanced calls: expected onHashingCompleted to be called");
                    break;
                default:
                    fail("Unexpected token " + token);
            }
        }
    }

    private static void assertRecordsInRankAreAscendingPathOrder(final HashingListener listener) {
        for (final List<VirtualRecord> rank : listener.ranks) {
            long prevPath = Path.INVALID_PATH;
            for (final VirtualRecord r : rank) {
                assertTrue(
                        prevPath < r.getPath(),
                        "Path not in ascending path order. prevPath=" + prevPath + ", path=" + r.getPath());
                prevPath = r.getPath();
            }
        }
    }

    /**
     * An implementation of {@link VirtualHashListener} used during testing. Specifically, we need to capture
     * the set of internal and leaf records that are sent to the listener during hashing to validate
     * that hashing visited everything we expect.
     */
    private static final class HashingListener implements VirtualHashListener<TestKey, TestValue> {
        static final char ON_HASHING_STARTED_SYMBOL = '{';
        static final char ON_HASHING_COMPLETED_SYMBOL = '}';
        static final char ON_BATCH_STARTED_SYMBOL = '[';
        static final char ON_BATCH_COMPLETED_SYMBOL = ']';
        static final char ON_RANK_STARTED_SYMBOL = '<';
        static final char ON_RANK_COMPLETED_SYMBOL = '>';
        static final char ON_INTERNAL_SYMBOL = 'I';
        static final char ON_LEAF_SYMBOL = 'L';

        private int onHashingStartedCallCount = 0;
        private int onBatchStartedCallCount = 0;
        private int onRankStartedCallCount = 0;
        private int onInternalHashedCallCount = 0;
        private int onLeafHashedCallCount = 0;
        private int onRankCompletedCallCount = 0;
        private int onBatchCompletedCallCount = 0;
        private int onHashingCompletedCallCount = 0;
        private final StringBuilder callHistory = new StringBuilder();

        private final Deque<List<VirtualRecord>> ranks = new ArrayDeque<>();
        private final Deque<List<VirtualInternalRecord>> internalBatches = new ArrayDeque<>();
        private final Deque<List<VirtualLeafRecord<TestKey, TestValue>>> leafBatches = new ArrayDeque<>();

        List<VirtualInternalRecord> unsortedInternals() {
            List<VirtualInternalRecord> records = new ArrayList<>();
            for (List<VirtualInternalRecord> list : internalBatches) {
                records.addAll(list);
            }
            return records;
        }

        List<VirtualLeafRecord<TestKey, TestValue>> sortedLeaves() {
            List<VirtualLeafRecord<TestKey, TestValue>> records = new ArrayList<>();
            for (List<VirtualLeafRecord<TestKey, TestValue>> list : leafBatches) {
                records.addAll(list);
            }
            records.sort(Comparator.comparingLong(VirtualRecord::getPath));
            return records;
        }

        @Override
        public void onHashingStarted() {
            onHashingStartedCallCount++;
            callHistory.append(ON_HASHING_STARTED_SYMBOL);
            internalBatches.clear();
            leafBatches.clear();
        }

        @Override
        public void onBatchStarted() {
            onBatchStartedCallCount++;
            callHistory.append(ON_BATCH_STARTED_SYMBOL);
            leafBatches.add(new ArrayList<>());
            internalBatches.add(new ArrayList<>());
        }

        @Override
        public void onRankStarted() {
            onRankStartedCallCount++;
            callHistory.append(ON_RANK_STARTED_SYMBOL);
            ranks.add(new ArrayList<>());
        }

        @Override
        public void onInternalHashed(VirtualInternalRecord internal) {
            onInternalHashedCallCount++;
            callHistory.append(ON_INTERNAL_SYMBOL);
            internalBatches.getLast().add(internal);
            ranks.getLast().add(internal);
        }

        @Override
        public void onLeafHashed(VirtualLeafRecord<TestKey, TestValue> leaf) {
            onLeafHashedCallCount++;
            callHistory.append(ON_LEAF_SYMBOL);
            leafBatches.getLast().add(leaf);
            ranks.getLast().add(leaf);
        }

        @Override
        public void onRankCompleted() {
            onRankCompletedCallCount++;
            callHistory.append(ON_RANK_COMPLETED_SYMBOL);
        }

        @Override
        public void onBatchCompleted() {
            onBatchCompletedCallCount++;
            callHistory.append(ON_BATCH_COMPLETED_SYMBOL);
        }

        @Override
        public void onHashingCompleted() {
            onHashingCompletedCallCount++;
            callHistory.append(ON_HASHING_COMPLETED_SYMBOL);
        }
    }
}
