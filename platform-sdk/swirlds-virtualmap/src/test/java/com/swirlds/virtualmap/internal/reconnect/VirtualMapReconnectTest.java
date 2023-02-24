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

package com.swirlds.virtualmap.internal.reconnect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.InMemoryBuilder;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Virtual Map Reconnect Test")
class VirtualMapReconnectTest extends VirtualMapReconnectTestBase {

    @Override
    protected VirtualDataSourceBuilder<TestKey, TestValue> createBuilder() {
        return new InMemoryBuilder();
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.1")})
    @DisplayName("Empty teacher and empty learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void emptyTeacherAndLearner() {
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.2")})
    @DisplayName("Empty teacher and full learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void emptyTeacherFullLearner() {
        learnerMap.put(A_KEY, APPLE);
        learnerMap.put(B_KEY, BANANA);
        learnerMap.put(C_KEY, CHERRY);
        learnerMap.put(D_KEY, DATE);
        learnerMap.put(E_KEY, EGGPLANT);
        learnerMap.put(F_KEY, FIG);
        learnerMap.put(G_KEY, GRAPE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.3")})
    @DisplayName("Full teacher and empty learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void fullTeacherEmptyLearner() {
        teacherMap.put(A_KEY, APPLE);
        teacherMap.put(B_KEY, BANANA);
        teacherMap.put(C_KEY, CHERRY);
        teacherMap.put(D_KEY, DATE);
        teacherMap.put(E_KEY, EGGPLANT);
        teacherMap.put(F_KEY, FIG);
        teacherMap.put(G_KEY, GRAPE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.4")})
    @DisplayName("Single-leaf teacher and empty learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void singleLeafTeacherEmptyLearner() {
        teacherMap.put(A_KEY, APPLE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.5")})
    @DisplayName("Empty teacher and single leaf learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void emptyTeacherSingleLeafLearner() {
        learnerMap.put(A_KEY, APPLE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.6")})
    @DisplayName("Two-leaf teacher and empty learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void twoLeafTeacherEmptyLearner() {
        teacherMap.put(A_KEY, APPLE);
        teacherMap.put(B_KEY, BANANA);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.7")})
    @DisplayName("Empty teacher and two-leaf learner")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void emptyTeacherTwoLeafLearner() {
        learnerMap.put(A_KEY, APPLE);
        learnerMap.put(B_KEY, BANANA);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.8")})
    @DisplayName("Teacher and Learner that are the same size but completely different")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void equalFullTeacherFullLearner() {
        teacherMap.put(A_KEY, AARDVARK);
        teacherMap.put(B_KEY, BEAR);
        teacherMap.put(C_KEY, CUTTLEFISH);
        teacherMap.put(D_KEY, DOG);
        teacherMap.put(E_KEY, EMU);
        teacherMap.put(F_KEY, FOX);
        teacherMap.put(G_KEY, GOOSE);

        learnerMap.put(A_KEY, APPLE);
        learnerMap.put(B_KEY, BANANA);
        learnerMap.put(C_KEY, CHERRY);
        learnerMap.put(D_KEY, DATE);
        learnerMap.put(E_KEY, EGGPLANT);
        learnerMap.put(F_KEY, FIG);
        learnerMap.put(G_KEY, GRAPE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.9")})
    @DisplayName("Equivalent teacher and learner that are full")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void sameSizeFullTeacherFullLearner() {
        teacherMap.put(A_KEY, APPLE);
        teacherMap.put(B_KEY, BANANA);
        teacherMap.put(C_KEY, CHERRY);
        teacherMap.put(D_KEY, DATE);
        teacherMap.put(E_KEY, EGGPLANT);
        teacherMap.put(F_KEY, FIG);
        teacherMap.put(G_KEY, GRAPE);

        learnerMap.put(A_KEY, APPLE);
        learnerMap.put(B_KEY, BANANA);
        learnerMap.put(C_KEY, CHERRY);
        learnerMap.put(D_KEY, DATE);
        learnerMap.put(E_KEY, EGGPLANT);
        learnerMap.put(F_KEY, FIG);
        learnerMap.put(G_KEY, GRAPE);

        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.10")})
    @DisplayName("Single leaf teacher and full learner where the leaf is the same")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void singleLeafTeacherFullLearner() {
        teacherMap.put(A_KEY, APPLE);

        learnerMap.put(A_KEY, APPLE);
        learnerMap.put(B_KEY, BANANA);
        learnerMap.put(C_KEY, CHERRY);
        learnerMap.put(D_KEY, DATE);
        learnerMap.put(E_KEY, EGGPLANT);
        learnerMap.put(F_KEY, FIG);
        learnerMap.put(G_KEY, GRAPE);

        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.11")})
    @DisplayName("Single leaf teacher and full learner where the leaf differs")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void singleLeafTeacherFullLearner2() {
        teacherMap.put(A_KEY, AARDVARK);

        learnerMap.put(A_KEY, APPLE);
        learnerMap.put(B_KEY, BANANA);
        learnerMap.put(C_KEY, CHERRY);
        learnerMap.put(D_KEY, DATE);
        learnerMap.put(E_KEY, EGGPLANT);
        learnerMap.put(F_KEY, FIG);
        learnerMap.put(G_KEY, GRAPE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.12")})
    @DisplayName("Full teacher and single-leaf learner where the leaf is equivalent")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void fullTeacherSingleLeafLearner() {
        teacherMap.put(A_KEY, AARDVARK);
        teacherMap.put(B_KEY, BEAR);
        teacherMap.put(C_KEY, CUTTLEFISH);
        teacherMap.put(D_KEY, DOG);
        teacherMap.put(E_KEY, EMU);
        teacherMap.put(F_KEY, FOX);
        teacherMap.put(G_KEY, GOOSE);

        learnerMap.put(A_KEY, AARDVARK);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.13")})
    @DisplayName("Full teacher and single-leaf learner where the leaf differs")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void fullTeacherSingleLeafLearner2() {
        teacherMap.put(A_KEY, AARDVARK);
        teacherMap.put(B_KEY, BEAR);
        teacherMap.put(C_KEY, CUTTLEFISH);
        teacherMap.put(D_KEY, DOG);
        teacherMap.put(E_KEY, EMU);
        teacherMap.put(F_KEY, FOX);
        teacherMap.put(G_KEY, GOOSE);

        learnerMap.put(A_KEY, APPLE);
        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @ParameterizedTest
    @MethodSource("provideSmallTreePermutations")
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-005"), @Tag("VMAP-006")})
    @DisplayName("Reconnect aborts 5 times before success")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void multipleAbortedReconnectsCanSucceed(int teacherStart, int teacherEnd, int learnerStart, int learnerEnd) {
        for (int i = teacherStart; i < teacherEnd; i++) {
            teacherMap.put(new TestKey(i), new TestValue(i));
        }

        for (int i = learnerStart; i < learnerEnd; i++) {
            learnerMap.put(new TestKey(i), new TestValue(i));
        }

        learnerBuilder.setNumCallsBeforeThrow((teacherEnd - teacherStart) / 2);
        learnerBuilder.setNumTimesToBreak(4);

        assertDoesNotThrow(() -> reconnectMultipleTimes(5), "Should not throw a Exception");
    }

    @Test
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect")})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void deleteAlreadyDeletedAccount() throws Exception {
        teacherMap.put(A_KEY, AARDVARK);
        teacherMap.put(B_KEY, BEAR);
        teacherMap.put(C_KEY, CUTTLEFISH);

        learnerMap.put(A_KEY, AARDVARK);
        learnerMap.put(B_KEY, BEAR);
        learnerMap.put(C_KEY, CUTTLEFISH); // leaf path value is 4

        // maps / caches should be identical at this point.  But now
        // remove a key (and add another) from the teacher, before reconnect starts.
        teacherMap.remove(C_KEY);
        teacherMap.put(D_KEY, DOG);

        final MerkleInternal teacherTree = createTreeForMap(teacherMap);
        final VirtualMap<TestKey, TestValue> copy = teacherMap.copy();
        final MerkleInternal learnerTree = createTreeForMap(learnerMap);

        // reconnect happening
        DummyMerkleInternal afterSyncLearnerTree = MerkleTestUtils.hashAndTestSynchronization(learnerTree, teacherTree);

        // not sure what is the better way to get the embedded Virtual map
        DummyMerkleInternal node = afterSyncLearnerTree.getChild(1);
        VirtualMap<TestKey, TestValue> afterMap = node.getChild(3);

        assertEquals(DOG, afterMap.get(D_KEY), "After sync, should have D_KEY available");
        assertNull(afterMap.get(C_KEY), "After sync, should not have C_KEY anymore");

        afterSyncLearnerTree.release();
        copy.release();
        teacherTree.release();
        learnerTree.release();
    }

    static Stream<Arguments> provideSmallTreePermutations() {
        final List<Arguments> args = new ArrayList<>();
        // Two large leaf trees that have no intersection
        args.add(Arguments.of(0, 1_000, 1_000, 2_000));
        // Two large leaf trees that intersect
        args.add(Arguments.of(0, 1_000, 500, 1_500));
        // A smaller tree and larger tree that do not intersect
        args.add(Arguments.of(0, 10, 1_000, 2_000));
        args.add(Arguments.of(1_000, 2_000, 0, 10));
        // A smaller tree and larger tree that do intersect
        args.add(Arguments.of(0, 10, 5, 1_005));
        args.add(Arguments.of(5, 1_005, 0, 10));

        // Two hundred leaf trees that intersect
        args.add(Arguments.of(50, 250, 0, 100));
        args.add(Arguments.of(50, 249, 0, 100));
        args.add(Arguments.of(50, 251, 0, 100));
        return args.stream();
    }
}
