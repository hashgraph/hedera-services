/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.cache;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyDoesNotThrow;
import static com.swirlds.virtualmap.internal.cache.VirtualNodeCache.DELETED_HASH;
import static com.swirlds.virtualmap.internal.cache.VirtualNodeCache.DELETED_LEAF_RECORD;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.test.fixtures.VirtualTestBase;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

class VirtualNodeCacheHammerTest extends VirtualTestBase {

    /**
     * This is perhaps the most crucial of all the tests here. We are going to build our
     * test tree, step by step. Initially, there are no nodes. Then we add A, B, C, etc.
     * We do this in a way that mimics what happens when {@link VirtualMap}
     * makes the calls. This should be a faithful reproduction of what we will actually see.
     * <p>
     * To complicate matters, once we build the tree, we start to tear it down again. We
     * also do this in order to try to replicate what will actually happen. We make this
     * even more rich by adding and removing nodes in different orders, so they end up
     * in different positions.
     * <p>
     * We create new caches along the way. We don't drop any of them until the end.
     */
    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache")})
    @DisplayName("Build a tree step by step")
    void buildATree() {
        // ROUND 0: Add A, B, and C. First add A, then B, then C. When we add C, we have to move A.
        // This will all happen in a single round. Then create the Root and Left internals after
        // creating the next round.

        // Add apple at path 1
        final VirtualNodeCache cache0 = cache;
        VirtualLeafBytes appleLeaf0 = appleLeaf(1);
        cache0.putLeaf(appleLeaf0);
        validateLeaves(cache0, 1, Collections.singletonList(appleLeaf0));

        // Add banana at path 2
        final VirtualLeafBytes bananaLeaf0 = bananaLeaf(2);
        cache0.putLeaf(bananaLeaf0);
        validateLeaves(cache0, 1, asList(appleLeaf0, bananaLeaf0));

        // Move apple to path 3
        appleLeaf0 = appleLeaf0.withPath(3);
        cache0.clearLeafPath(1);
        cache0.putLeaf(appleLeaf0);
        assertEquals(DELETED_LEAF_RECORD, cache0.lookupLeafByPath(1, false), "leaf should have been deleted");
        validateLeaves(cache0, 2, asList(bananaLeaf0, appleLeaf0));

        // Add cherry to path 4
        final VirtualLeafBytes cherryLeaf0 = cherryLeaf(4);
        cache0.putLeaf(cherryLeaf0);
        validateLeaves(cache0, 2, asList(bananaLeaf0, appleLeaf0, cherryLeaf0));

        // End the round and create the next round
        nextRound();
        validateDirtyLeaves(asList(bananaLeaf0, appleLeaf0, cherryLeaf0), cache0.dirtyLeavesForHash(2, 4));

        // Add an internal node "left" at index 1 and then root at index 0
        final VirtualHashRecord leftInternal0 = leftInternal();
        final VirtualHashRecord rootInternal0 = rootInternal();
        cache0.putHash(leftInternal0);
        cache0.putHash(rootInternal0);
        cache0.seal();
        validateTree(cache0, asList(rootInternal0, leftInternal0, bananaLeaf0, appleLeaf0, cherryLeaf0));
        final Hash bananaLeaf0intHash = cache0.lookupHashByPath(bananaLeaf0.path(), false);
        assertNull(bananaLeaf0intHash);
        final Hash appleLeaf0intHash = cache0.lookupHashByPath(appleLeaf0.path(), false);
        assertNull(appleLeaf0intHash);
        final Hash cherryLeaf0intHash = cache0.lookupHashByPath(cherryLeaf0.path(), false);
        assertNull(cherryLeaf0intHash);
        // This check (and many similar checks below) is arguable. In real world, dirtyHashes() is only
        // called when a cache is flushed to disk, and it happens only after VirtualMap copy is hashed, all
        // hashes are calculated and put to the cache. Here the cache doesn't contain hashes for dirty leaves
        // (bananaLeaf0, appleLeaf0, cherryLeaf0). Should dirtyHashes() include these leaf nodes? Currently
        // it doesn't
        validateDirtyInternals(Set.of(rootInternal0, leftInternal0), cache0.dirtyHashesForFlush(4));

        // ROUND 1: Add D and E.
        final VirtualNodeCache cache1 = cache;

        // Move B to index 5
        final VirtualLeafBytes bananaLeaf1 = bananaLeaf(5);
        cache1.clearLeafPath(2);
        cache1.putLeaf(bananaLeaf1);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache1.lookupLeafByPath(2, false),
                "value that was looked up should match original value");
        validateLeaves(cache1, 3, asList(appleLeaf0, cherryLeaf0, bananaLeaf1));

        // Add D at index 6
        final VirtualLeafBytes dateLeaf1 = dateLeaf(6);
        cache1.putLeaf(dateLeaf1);
        validateLeaves(cache1, 3, asList(appleLeaf0, cherryLeaf0, bananaLeaf1, dateLeaf1));

        // Move A to index 7
        final VirtualLeafBytes appleLeaf1 = appleLeaf(7);
        cache1.clearLeafPath(3);
        cache1.putLeaf(appleLeaf1);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache1.lookupLeafByPath(3, false),
                "value that was looked up should match original value");
        validateLeaves(cache1, 4, asList(cherryLeaf0, bananaLeaf1, dateLeaf1, appleLeaf1));

        // Add E at index 8
        final VirtualLeafBytes eggplantLeaf1 = eggplantLeaf(8);
        cache1.putLeaf(eggplantLeaf1);
        validateLeaves(cache1, 4, asList(cherryLeaf0, bananaLeaf1, dateLeaf1, appleLeaf1, eggplantLeaf1));

        // End the round and create the next round
        nextRound();
        validateDirtyLeaves(asList(bananaLeaf1, dateLeaf1, appleLeaf1, eggplantLeaf1), cache1.dirtyLeavesForHash(4, 8));

        // Add an internal node "leftLeft" at index 3 and then "right" at index 2
        final VirtualHashRecord leftLeftInternal1 = leftLeftInternal();
        final VirtualHashRecord rightInternal1 = rightInternal();
        final VirtualHashRecord leftInternal1 = leftInternal();
        final VirtualHashRecord rootInternal1 = rootInternal();
        cache1.putHash(leftLeftInternal1);
        cache1.putHash(rightInternal1);
        cache1.putHash(leftInternal1);
        cache1.putHash(rootInternal1);
        cache1.seal();
        validateTree(
                cache1,
                asList(
                        rootInternal1,
                        leftInternal1,
                        rightInternal1,
                        leftLeftInternal1,
                        cherryLeaf0,
                        bananaLeaf1,
                        dateLeaf1,
                        appleLeaf1,
                        eggplantLeaf1));
        validateDirtyInternals(
                Set.of(rootInternal1, leftInternal1, rightInternal1, leftLeftInternal1), cache1.dirtyHashesForFlush(8));

        // ROUND 2: Add F and G
        final VirtualNodeCache cache2 = cache;

        // Move C to index 9
        final VirtualLeafBytes cherryLeaf2 = cherryLeaf(9);
        cache2.clearLeafPath(4);
        cache2.putLeaf(cherryLeaf2);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache2.lookupLeafByPath(4, false),
                "value that was looked up should match original value");
        validateLeaves(cache2, 5, asList(bananaLeaf1, dateLeaf1, appleLeaf1, eggplantLeaf1, cherryLeaf2));

        // Add F at index 10
        final VirtualLeafBytes figLeaf2 = figLeaf(10);
        cache2.putLeaf(figLeaf2);
        validateLeaves(cache2, 5, asList(bananaLeaf1, dateLeaf1, appleLeaf1, eggplantLeaf1, cherryLeaf2, figLeaf2));

        // Move B to index 11
        final VirtualLeafBytes bananaLeaf2 = bananaLeaf(11);
        cache2.clearLeafPath(5);
        cache2.putLeaf(bananaLeaf2);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache2.lookupLeafByPath(5, false),
                "value that was looked up should match original value");
        validateLeaves(cache2, 6, asList(dateLeaf1, appleLeaf1, eggplantLeaf1, cherryLeaf2, figLeaf2, bananaLeaf2));

        // Add G at index 12
        final VirtualLeafBytes grapeLeaf2 = grapeLeaf(12);
        cache2.putLeaf(grapeLeaf2);
        validateLeaves(
                cache2,
                6,
                asList(dateLeaf1, appleLeaf1, eggplantLeaf1, cherryLeaf2, figLeaf2, bananaLeaf2, grapeLeaf2));

        // End the round and create the next round
        nextRound();
        validateDirtyLeaves(asList(cherryLeaf2, figLeaf2, bananaLeaf2, grapeLeaf2), cache2.dirtyLeavesForHash(6, 12));

        // Add an internal node "rightLeft" at index 5 and then "leftRight" at index 4
        final VirtualHashRecord rightLeftInternal2 = rightLeftInternal();
        final VirtualHashRecord leftRightInternal2 = leftRightInternal();
        final VirtualHashRecord rightInternal2 = rightInternal();
        final VirtualHashRecord leftInternal2 = leftInternal();
        final VirtualHashRecord rootInternal2 = rootInternal();
        cache2.putHash(rightLeftInternal2);
        cache2.putHash(leftRightInternal2);
        cache2.putHash(rightInternal2);
        cache2.putHash(leftInternal2);
        cache2.putHash(rootInternal2);
        cache2.seal();
        validateTree(
                cache2,
                asList(
                        rootInternal2,
                        leftInternal2,
                        rightInternal2,
                        leftLeftInternal1,
                        leftRightInternal2,
                        rightLeftInternal2,
                        dateLeaf1,
                        appleLeaf1,
                        eggplantLeaf1,
                        cherryLeaf2,
                        figLeaf2,
                        bananaLeaf2,
                        grapeLeaf2));
        validateDirtyInternals(
                Set.of(rootInternal2, leftInternal2, rightInternal2, leftRightInternal2, rightLeftInternal2),
                cache2.dirtyHashesForFlush(12));

        // Now it is time to start mutating the tree. Some leaves will be removed and re-added, some
        // will be removed and replaced with a new value (same key).

        // Remove A and move G to take its place. Move B to path 5
        final VirtualNodeCache cache3 = cache;
        VirtualLeafBytes appleLeaf3 = appleLeaf(7);
        cache3.deleteLeaf(appleLeaf3);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache3.lookupLeafByPath(7, false),
                "value that was looked up should match original value");

        final VirtualLeafBytes grapeLeaf3 = grapeLeaf(7);
        cache3.clearLeafPath(12);
        cache3.putLeaf(grapeLeaf3);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache3.lookupLeafByPath(12, false),
                "value that was looked up should match original value");

        VirtualLeafBytes bananaLeaf3 = bananaLeaf(5);
        cache3.clearLeafPath(11);
        cache3.putLeaf(bananaLeaf3);
        cache3.deleteHash(5);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache3.lookupLeafByPath(11, false),
                "value that was looked up should match original value");
        assertEquals(
                DELETED_HASH,
                cache3.lookupHashByPath(5, false),
                "value that was looked up should match original value");

        validateLeaves(cache3, 5, asList(bananaLeaf3, dateLeaf1, grapeLeaf3, eggplantLeaf1, cherryLeaf2, figLeaf2));

        // Add A back in at position 11 and move Banana to position 12.
        appleLeaf3 = appleLeaf3.withPath(11);
        cache3.putLeaf(appleLeaf3);
        bananaLeaf3 = bananaLeaf3.withPath(12);
        cache3.putLeaf(bananaLeaf3);
        cache3.clearLeafPath(5);
        assertEquals(
                DELETED_LEAF_RECORD,
                cache3.lookupLeafByPath(5, false),
                "value that was looked up should match original value");

        validateLeaves(
                cache3,
                6,
                asList(dateLeaf1, grapeLeaf3, eggplantLeaf1, cherryLeaf2, figLeaf2, appleLeaf3, bananaLeaf3));

        final VirtualLeafBytes dogLeaf3 = dogLeaf(dateLeaf1.path());
        cache3.putLeaf(dogLeaf3);

        final VirtualLeafBytes foxLeaf3 = foxLeaf(figLeaf2.path());
        cache3.putLeaf(foxLeaf3);

        validateLeaves(
                cache3, 6, asList(dogLeaf3, grapeLeaf3, eggplantLeaf1, cherryLeaf2, foxLeaf3, appleLeaf3, bananaLeaf3));

        // End the round and create the next round
        nextRound();
        validateDirtyLeaves(
                asList(dogLeaf3, grapeLeaf3, foxLeaf3, appleLeaf3, bananaLeaf3), cache3.dirtyLeavesForHash(6, 12));

        // We removed the internal node rightLeftInternal. We need to add it back in.
        final VirtualHashRecord rightLeftInternal3 = rightLeftInternal();
        final VirtualHashRecord leftRightInternal3 = leftRightInternal();
        final VirtualHashRecord leftLeftInternal3 = leftLeftInternal();
        final VirtualHashRecord rightInternal3 = rightInternal();
        final VirtualHashRecord leftInternal3 = leftInternal();
        final VirtualHashRecord rootInternal3 = rootInternal();
        cache3.putHash(rightLeftInternal3);
        cache3.putHash(leftRightInternal3);
        cache3.putHash(leftLeftInternal3);
        cache3.putHash(rightInternal3);
        cache3.putHash(leftInternal3);
        cache3.putHash(rootInternal3);
        cache3.seal();
        validateDirtyInternals(
                Set.of(
                        rootInternal3,
                        leftInternal3,
                        rightInternal3,
                        leftLeftInternal3,
                        leftRightInternal3,
                        rightLeftInternal3),
                cache3.dirtyHashesForFlush(12));

        // At this point, we have built the tree successfully. Verify one more time that each version of
        // the cache still sees things the same way it did at the time the copy was made.
        final VirtualNodeCache cache4 = cache;
        validateTree(cache0, asList(rootInternal0, leftInternal0, bananaLeaf0, appleLeaf0, cherryLeaf0));
        validateTree(
                cache1,
                asList(
                        rootInternal1,
                        leftInternal1,
                        rightInternal1,
                        leftLeftInternal1,
                        cherryLeaf0,
                        bananaLeaf1,
                        dateLeaf1,
                        appleLeaf1,
                        eggplantLeaf1));
        validateTree(
                cache2,
                asList(
                        rootInternal2,
                        leftInternal2,
                        rightInternal2,
                        leftLeftInternal1,
                        leftRightInternal2,
                        rightLeftInternal2,
                        dateLeaf1,
                        appleLeaf1,
                        eggplantLeaf1,
                        cherryLeaf2,
                        figLeaf2,
                        bananaLeaf2,
                        grapeLeaf2));
        validateTree(
                cache3,
                asList(
                        rootInternal3,
                        leftInternal3,
                        rightInternal3,
                        leftLeftInternal3,
                        leftRightInternal3,
                        rightLeftInternal3,
                        dogLeaf3,
                        grapeLeaf3,
                        eggplantLeaf1,
                        cherryLeaf2,
                        foxLeaf3,
                        appleLeaf3,
                        bananaLeaf3));
        validateTree(
                cache4,
                asList(
                        rootInternal3,
                        leftInternal3,
                        rightInternal3,
                        leftLeftInternal3,
                        leftRightInternal3,
                        rightLeftInternal3,
                        dogLeaf3,
                        grapeLeaf3,
                        eggplantLeaf1,
                        cherryLeaf2,
                        foxLeaf3,
                        appleLeaf3,
                        bananaLeaf3));

        // Now, we will release the oldest, cache0
        cache0.release();
        final List<?> version1Nodes = asList(
                rootInternal1,
                leftInternal1,
                rightInternal1,
                leftLeftInternal1,
                null,
                bananaLeaf1,
                dateLeaf1,
                appleLeaf1,
                eggplantLeaf1);
        final List<?> version2Nodes = asList(
                rootInternal2,
                leftInternal2,
                rightInternal2,
                leftLeftInternal1,
                leftRightInternal2,
                rightLeftInternal2,
                dateLeaf1,
                appleLeaf1,
                eggplantLeaf1,
                cherryLeaf2,
                figLeaf2,
                bananaLeaf2,
                grapeLeaf2);
        final List<?> version3Nodes = asList(
                rootInternal3,
                leftInternal3,
                rightInternal3,
                leftLeftInternal3,
                leftRightInternal3,
                rightLeftInternal3,
                dogLeaf3,
                grapeLeaf3,
                eggplantLeaf1,
                cherryLeaf2,
                foxLeaf3,
                appleLeaf3,
                bananaLeaf3);
        final List<?> version4Nodes = asList(
                rootInternal3,
                leftInternal3,
                rightInternal3,
                leftLeftInternal3,
                leftRightInternal3,
                rightLeftInternal3,
                dogLeaf3,
                grapeLeaf3,
                eggplantLeaf1,
                cherryLeaf2,
                foxLeaf3,
                appleLeaf3,
                bananaLeaf3);
        assertEventuallyDoesNotThrow(
                () -> {
                    validateTree(cache1, version1Nodes);
                    validateTree(cache2, version2Nodes);
                    validateTree(cache3, version3Nodes);
                    validateTree(cache4, version4Nodes);
                },
                Duration.ofSeconds(1),
                "expected cache to eventually become clean");

        // Now we will release the next oldest, cache 1
        cache1.release();
        final List<?> version2Nodes1 = asList(
                rootInternal2,
                leftInternal2,
                rightInternal2,
                null,
                leftRightInternal2,
                rightLeftInternal2,
                null,
                null,
                null,
                cherryLeaf2,
                figLeaf2,
                bananaLeaf2,
                grapeLeaf2);
        final List<?> version3Nodes1 = asList(
                rootInternal3,
                leftInternal3,
                rightInternal3,
                leftLeftInternal3,
                leftRightInternal3,
                rightLeftInternal3,
                dogLeaf3,
                grapeLeaf3,
                null,
                cherryLeaf2,
                foxLeaf3,
                appleLeaf3,
                bananaLeaf3);
        final List<?> version4Nodes1 = asList(
                rootInternal3,
                leftInternal3,
                rightInternal3,
                leftLeftInternal3,
                leftRightInternal3,
                rightLeftInternal3,
                dogLeaf3,
                grapeLeaf3,
                null,
                cherryLeaf2,
                foxLeaf3,
                appleLeaf3,
                bananaLeaf3);
        assertEventuallyDoesNotThrow(
                () -> {
                    validateTree(cache2, version2Nodes1);
                    validateTree(cache3, version3Nodes1);
                    validateTree(cache4, version4Nodes1);
                },
                Duration.ofSeconds(1),
                "expected cache to eventually become clean");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where all mutations are in the same version and none are deleted")
    void dirtyLeaves_allInSameVersionNoneDeleted() {
        final VirtualNodeCache cache = new VirtualNodeCache();
        cache.putLeaf(appleLeaf(7));
        cache.putLeaf(bananaLeaf(5));
        cache.putLeaf(cherryLeaf(4));
        cache.putLeaf(dateLeaf(6));
        cache.putLeaf(eggplantLeaf(8));
        cache.seal();

        final List<VirtualLeafBytes> leaves = cache.dirtyLeavesForHash(4, 8).toList();
        assertEquals(5, leaves.size(), "All leaves should be dirty");
        assertEquals(cherryLeaf(4), leaves.get(0), "Unexpected leaf");
        assertEquals(bananaLeaf(5), leaves.get(1), "Unexpected leaf");
        assertEquals(dateLeaf(6), leaves.get(2), "Unexpected leaf");
        assertEquals(appleLeaf(7), leaves.get(3), "Unexpected leaf");
        assertEquals(eggplantLeaf(8), leaves.get(4), "Unexpected leaf");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where all mutations are in the same version and some are deleted")
    void dirtyLeaves_allInSameVersionSomeDeleted() {
        final VirtualNodeCache cache = new VirtualNodeCache();
        cache.putLeaf(appleLeaf(7));
        cache.putLeaf(bananaLeaf(5));
        cache.putLeaf(cherryLeaf(4));
        cache.putLeaf(dateLeaf(6));
        cache.putLeaf(eggplantLeaf(8));

        cache.deleteLeaf(eggplantLeaf(8));
        cache.putLeaf(appleLeaf(3));
        cache.seal();

        final List<VirtualLeafBytes> leaves = cache.dirtyLeavesForHash(3, 6).toList();
        assertEquals(4, leaves.size(), "Some leaves should be dirty");
        assertEquals(appleLeaf(3), leaves.get(0), "Unexpected leaf");
        assertEquals(cherryLeaf(4), leaves.get(1), "Unexpected leaf");
        assertEquals(bananaLeaf(5), leaves.get(2), "Unexpected leaf");
        assertEquals(dateLeaf(6), leaves.get(3), "Unexpected leaf");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where all mutations are in the same version and all are deleted")
    void dirtyLeaves_allInSameVersionAllDeleted() {
        final VirtualNodeCache cache = new VirtualNodeCache();
        cache.putLeaf(appleLeaf(7));
        cache.putLeaf(bananaLeaf(5));
        cache.putLeaf(cherryLeaf(4));
        cache.putLeaf(dateLeaf(6));
        cache.putLeaf(eggplantLeaf(8));

        // I will delete them in random order, and when I delete one I need to rearrange things accordingly

        // Delete Banana
        cache.deleteLeaf(bananaLeaf(5));
        cache.putLeaf(eggplantLeaf(5));
        cache.putLeaf(appleLeaf(3));

        // Delete Date
        cache.deleteLeaf(dateLeaf(6));
        cache.putLeaf(eggplantLeaf(2));

        // Delete Eggplant
        cache.deleteLeaf(eggplantLeaf(2));
        cache.putLeaf(cherryLeaf(2));
        cache.putLeaf(appleLeaf(1));

        // Delete apple
        cache.deleteLeaf(appleLeaf(1));
        cache.putLeaf(cherryLeaf(1));

        // Delete cherry
        cache.deleteLeaf(cherryLeaf(1));
        cache.seal();

        final List<VirtualLeafBytes> leaves = cache.dirtyLeavesForFlush(-1, -1).toList();
        assertEquals(0, leaves.size(), "All leaves should be missing");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where all mutations are in the same version and some paths have hosted multiple leaves")
    void dirtyLeaves_allInSameVersionSomeDeletedPathConflict() {
        final VirtualNodeCache cache = new VirtualNodeCache();
        cache.putLeaf(appleLeaf(7));
        cache.putLeaf(bananaLeaf(5));
        cache.putLeaf(cherryLeaf(4));
        cache.putLeaf(dateLeaf(6));
        cache.putLeaf(eggplantLeaf(8));

        // This is actually a tricky scenario where we get two mutation with the same
        // path and the same version, but different keys and different "deleted" status.
        // This scenario was failing when the test was written.

        // Delete Eggplant
        cache.deleteLeaf(eggplantLeaf(8));
        cache.putLeaf(appleLeaf(3));

        // Delete Cherry
        cache.deleteLeaf(cherryLeaf(4));
        cache.putLeaf(dateLeaf(4));
        cache.putLeaf(bananaLeaf(2));
        cache.seal();

        final List<VirtualLeafBytes> leaves = cache.dirtyLeavesForHash(2, 4).toList();
        assertEquals(3, leaves.size(), "Should only have three leaves");
        assertEquals(bananaLeaf(2), leaves.get(0), "Unexpected leaf");
        assertEquals(appleLeaf(3), leaves.get(1), "Unexpected leaf");
        assertEquals(dateLeaf(4), leaves.get(2), "Unexpected leaf");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where mutations are across versions and none are deleted")
    void dirtyLeaves_differentVersionsNoneDeleted() {
        // NOTE: In all these tests I don't bother with clearLeafPath since I'm not getting leave paths
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        cache0.putLeaf(appleLeaf(1));

        final VirtualNodeCache cache1 = cache0.copy();
        cache1.putLeaf(bananaLeaf(2));
        cache1.putLeaf(appleLeaf(3));
        cache1.putLeaf(cherryLeaf(4));

        final VirtualNodeCache cache2 = cache1.copy();
        cache2.putLeaf(bananaLeaf(5));
        cache2.putLeaf(dateLeaf(6));
        cache2.putLeaf(appleLeaf(7));
        cache2.putLeaf(eggplantLeaf(8));

        cache0.seal();
        cache1.seal();
        cache2.seal();

        cache0.merge();
        cache1.merge();

        final Set<VirtualLeafBytes> leaves = cache2.dirtyLeavesForFlush(4, 8).collect(Collectors.toSet());
        assertEquals(5, leaves.size(), "All leaves should be dirty");
        assertEquals(Set.of(cherryLeaf(4), bananaLeaf(5), dateLeaf(6), appleLeaf(7), eggplantLeaf(8)), leaves);
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where mutations are across versions and some are deleted")
    void dirtyLeaves_differentVersionsSomeDeleted() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        cache0.putLeaf(appleLeaf(1));

        final VirtualNodeCache cache1 = cache0.copy();
        cache1.putLeaf(bananaLeaf(2));
        cache1.putLeaf(appleLeaf(3));
        cache1.deleteLeaf(appleLeaf(3));
        cache1.putLeaf(figLeaf(3));
        cache1.putLeaf(cherryLeaf(4));

        final VirtualNodeCache cache2 = cache1.copy();
        cache2.putLeaf(bananaLeaf(5));
        cache2.putLeaf(dateLeaf(6));
        cache2.deleteLeaf(bananaLeaf(5));
        cache2.putLeaf(dateLeaf(5));
        cache2.putLeaf(grapeLeaf(6));
        cache2.putLeaf(figLeaf(7));
        cache2.putLeaf(eggplantLeaf(8));
        cache2.deleteLeaf(cherryLeaf(4));
        cache2.putLeaf(eggplantLeaf(4));
        cache2.putLeaf(figLeaf(3));

        cache0.seal();
        cache1.seal();
        cache2.seal();

        cache0.merge();
        cache1.merge();

        final Set<VirtualLeafBytes> leaves = cache2.dirtyLeavesForFlush(3, 6).collect(Collectors.toSet());
        assertEquals(4, leaves.size(), "Some leaves should be dirty");
        assertEquals(Set.of(figLeaf(3), eggplantLeaf(4), dateLeaf(5), grapeLeaf(6)), leaves);
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves where mutations are across versions and all are deleted")
    void dirtyLeaves_differentVersionsAllDeleted() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        cache0.putLeaf(appleLeaf(1));
        cache0.putLeaf(bananaLeaf(2));
        cache0.putLeaf(appleLeaf(3));
        cache0.putLeaf(cherryLeaf(4));
        cache0.deleteLeaf(appleLeaf(3));
        cache0.putLeaf(cherryLeaf(1));

        final VirtualNodeCache cache1 = cache0.copy();
        cache1.putLeaf(cherryLeaf(3));
        cache1.putLeaf(dateLeaf(4));
        cache1.deleteLeaf(bananaLeaf(2));
        cache1.putLeaf(dateLeaf(2));
        cache1.putLeaf(cherryLeaf(1));

        final VirtualNodeCache cache2 = cache1.copy();
        cache2.putLeaf(cherryLeaf(3));
        cache2.putLeaf(eggplantLeaf(4));
        cache2.deleteLeaf(cherryLeaf(3));
        cache2.deleteLeaf(eggplantLeaf(1));
        cache2.deleteLeaf(dateLeaf(2));
        cache2.deleteLeaf(eggplantLeaf(1));

        cache0.seal();
        cache1.seal();
        cache2.seal();

        cache0.merge();
        cache1.merge();

        final List<VirtualLeafBytes> leaves = cache2.dirtyLeavesForFlush(-1, -1).toList();
        assertEquals(0, leaves.size(), "All leaves should be deleted");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyInternals")})
    @DisplayName("dirtyInternals where all mutations are in the same version and none are deleted")
    void dirtyInternals_allInSameVersionNoneDeleted() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        cache0.copy(); // Needed until #3842 is fixed

        cache0.putHash(rootInternal());
        cache0.putHash(leftInternal());
        cache0.putHash(rightInternal());
        cache0.putHash(leftLeftInternal());
        cache0.putHash(leftRightInternal());
        cache0.putHash(rightLeftInternal());
        cache0.seal();

        final List<VirtualHashRecord> internals = cache0.dirtyHashesForFlush(12).toList();
        assertEquals(6, internals.size(), "All internals should be dirty");
        assertEquals(rootInternal(), internals.get(0), "Unexpected internal");
        assertEquals(leftInternal(), internals.get(1), "Unexpected internal");
        assertEquals(rightInternal(), internals.get(2), "Unexpected internal");
        assertEquals(leftLeftInternal(), internals.get(3), "Unexpected internal");
        assertEquals(leftRightInternal(), internals.get(4), "Unexpected internal");
        assertEquals(rightLeftInternal(), internals.get(5), "Unexpected internal");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyInternals")})
    @DisplayName("dirtyInternals where mutations are across versions and none are deleted")
    void dirtyInternals_differentVersionsNoneDeleted() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        final VirtualNodeCache cache1 = cache0.copy();
        cache0.putHash(rootInternal());
        cache0.putHash(leftInternal());
        cache0.putHash(rightInternal());
        cache1.copy(); // Needed until #3842 is fixed
        cache1.putHash(leftLeftInternal());
        cache1.putHash(leftRightInternal());
        cache1.putHash(rightLeftInternal());
        cache0.seal();
        cache1.seal();
        cache0.merge();

        final List<VirtualHashRecord> internals = cache1.dirtyHashesForFlush(12).toList();
        assertEquals(6, internals.size(), "All internals should be dirty");
        assertEquals(
                Set.of(
                        rootInternal(),
                        leftInternal(),
                        rightInternal(),
                        leftLeftInternal(),
                        leftRightInternal(),
                        rightLeftInternal()),
                new HashSet<>(internals),
                "All internals should be dirty");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyInternals")})
    @DisplayName("dirtyInternals where mutations are across versions and some are deleted")
    void dirtyInternals_differentVersionsSomeDeleted() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        final VirtualNodeCache cache1 = cache0.copy();
        cache0.putHash(rootInternal());
        cache0.putHash(leftInternal());
        cache0.putHash(rightInternal());
        cache1.deleteHash(2);

        final VirtualNodeCache cache2 = cache1.copy();
        cache1.putHash(rightInternal());
        cache1.putHash(leftLeftInternal());
        cache1.putHash(leftRightInternal());
        cache2.deleteHash(4);
        cache2.deleteHash(3);

        cache2.copy(); // Needed until #3842 is fixed
        cache2.putHash(leftLeftInternal());
        cache2.putHash(leftRightInternal());
        cache2.putHash(rightLeftInternal());

        cache0.seal();
        cache1.seal();
        cache2.seal();
        cache0.merge();
        cache1.merge();

        final List<VirtualHashRecord> internals = cache2.dirtyHashesForFlush(12).toList();
        assertEquals(6, internals.size(), "All internals should be dirty");
        assertEquals(
                Set.of(
                        rootInternal(),
                        leftInternal(),
                        rightInternal(),
                        leftLeftInternal(),
                        leftRightInternal(),
                        rightLeftInternal()),
                new HashSet<>(internals),
                "All internals should be dirty");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyInternals")})
    @DisplayName("dirtyInternals where mutations are across versions and all are deleted")
    void dirtyInternals_differentVersionsAllDeleted() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        final VirtualNodeCache cache1 = cache0.copy();
        cache0.putHash(rootInternal());
        cache0.putHash(leftInternal());
        cache0.putHash(rightInternal());
        cache0.putHash(leftLeftInternal());
        cache0.putHash(leftRightInternal());
        cache0.putHash(rightLeftInternal());
        cache1.deleteHash(6);
        cache1.deleteHash(5);
        cache1.deleteHash(4);

        final VirtualNodeCache cache2 = cache1.copy();
        cache1.putHash(leftLeftInternal());
        cache2.deleteHash(4);
        cache2.deleteHash(3);
        cache2.deleteHash(2);
        cache2.deleteHash(1);
        cache2.deleteHash(0);

        cache2.copy();
        cache0.seal();
        cache1.seal();
        cache2.seal();
        cache0.merge();
        cache1.merge();

        final List<VirtualHashRecord> internals = cache2.dirtyHashesForFlush(-1).toList();
        assertEquals(0, internals.size(), "No internals should be dirty");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("VirtualNodeCache"), @Tag("DirtyLeaves")})
    @DisplayName("dirtyLeaves for hashing and flushes do not affect each other")
    void dirtyLeaves_flushesAndHashing() {
        final VirtualNodeCache cache0 = new VirtualNodeCache();
        cache0.putLeaf(appleLeaf(1));
        cache0.putLeaf(bananaLeaf(2));

        final VirtualNodeCache cache1 = cache0.copy();
        cache0.seal();
        cache1.deleteLeaf(appleLeaf(1));
        cache1.putLeaf(appleLeaf(3));
        cache1.putLeaf(cherryLeaf(4));

        // Hash version 0
        final List<VirtualLeafBytes> dirtyLeaves0H =
                cache0.dirtyLeavesForHash(1, 2).toList();
        assertEquals(List.of(appleLeaf(1), bananaLeaf(2)), dirtyLeaves0H);

        cache1.copy();
        cache1.seal();

        // Hash version 1
        final List<VirtualLeafBytes> dirtyLeaves1 =
                cache1.dirtyLeavesForHash(2, 4).toList();
        assertEquals(List.of(appleLeaf(3), cherryLeaf(4)), dirtyLeaves1);

        // Flush version 0
        final Set<VirtualLeafBytes> dirtyLeaves0F =
                cache0.dirtyLeavesForFlush(1, 2).collect(Collectors.toSet());
        assertEquals(Set.of(appleLeaf(1), bananaLeaf(2)), dirtyLeaves0F);
    }

    // ----------------------------------------------------------------------
    // Test Utility methods
    // ----------------------------------------------------------------------

    private void validateLeaves(
            final VirtualNodeCache cache, final long firstLeafPath, final List<VirtualLeafBytes> leaves) {
        long expectedPath = firstLeafPath;
        for (final VirtualLeafBytes leaf : leaves) {
            assertEquals(expectedPath, leaf.path(), "path should match expected path");
            assertEquals(
                    leaf,
                    cache.lookupLeafByPath(leaf.path(), false),
                    "value that was looked up should match original value");
            assertEquals(
                    leaf,
                    cache.lookupLeafByKey(leaf.keyBytes(), false),
                    "value that was looked up should match original value");
            expectedPath++;
        }
    }

    private void validateDirtyLeaves(final List<VirtualLeafBytes> expected, final Stream<VirtualLeafBytes> stream) {
        final List<VirtualLeafBytes> dirty = stream.toList();
        assertEquals(expected.size(), dirty.size(), "dirtyLeaves did not have the expected number of elements");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), dirty.get(i), "value that was looked up should match expected value");
        }
    }

    private void validateDirtyInternals(final Set<VirtualHashRecord> expected, final Stream<VirtualHashRecord> actual) {
        final List<VirtualHashRecord> dirty = actual.toList();
        assertEquals(expected.size(), dirty.size(), "dirtyInternals did not have the expected number of elements");
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(expected.contains(dirty.get(i)), "unexpected value");
        }
    }

    private void validateTree(final VirtualNodeCache cache, final List<?> nodes) {
        long expectedPath = 0;
        for (final Object node : nodes) {
            if (node == null) {
                // This signals that a leaf has fallen out of the cache.
                assertNull(cache.lookupLeafByPath(expectedPath, false), "no value should be found");
                assertNull(cache.lookupHashByPath(expectedPath, false), "no value should be found");
                expectedPath++;
            } else {
                if (node instanceof VirtualLeafRecord virtualLeafRecord) {
                    assertEquals(expectedPath, virtualLeafRecord.getPath(), "path should match the expected value");
                    final VirtualLeafBytes leaf = (VirtualLeafBytes) node;
                    assertEquals(
                            leaf,
                            cache.lookupLeafByPath(leaf.path(), false),
                            "value that was looked up should match original value");
                    assertEquals(
                            leaf,
                            cache.lookupLeafByKey(leaf.keyBytes(), false),
                            "value that was looked up should match original value");
                } else if (node instanceof VirtualHashRecord virtualHashRecord) {
                    assertEquals(
                            virtualHashRecord.hash(),
                            cache.lookupHashByPath(virtualHashRecord.path(), false),
                            "value that was looked up should match original value");
                } else {
                    throw new IllegalArgumentException("Unexpected node type: " + node.getClass());
                }
                expectedPath++;
            }
        }
    }
}
