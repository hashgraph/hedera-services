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

package com.swirlds.virtual.merkle.reconnect;

import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIME_CONSUMING;
import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestValue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag(TIMING_SENSITIVE)
@DisplayName("Virtual Map MerkleDB Large Reconnect Test")
class VirtualMapLargeReconnectTest extends VirtualMapReconnectTestBase {

    @ParameterizedTest
    @MethodSource("provideLargeTreePermutations")
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-003"), @Tag("VMAP-003.14")})
    @Tag(TIME_CONSUMING)
    @DisplayName("Permutations of very large trees reconnecting")
    // FUTURE WORK: https://github.com/hashgraph/hedera-services/issues/11507
    @Disabled
    void largeTeacherLargerLearnerPermutations(int teacherStart, int teacherEnd, int learnerStart, int learnerEnd) {

        for (int i = teacherStart; i < teacherEnd; i++) {
            teacherMap.put(new TestKey(i), new TestValue(i));
        }

        for (int i = learnerStart; i < learnerEnd; i++) {
            learnerMap.put(new TestKey(i), new TestValue(i));
        }

        assertDoesNotThrow(this::reconnect, "Should not throw a Exception");
    }

    @ParameterizedTest
    @MethodSource("provideLargeTreePermutations")
    @Tags({@Tag("VirtualMerkle"), @Tag("Reconnect"), @Tag("VMAP-005"), @Tag("VMAP-006")})
    @Tag(TIME_CONSUMING)
    @DisplayName("Reconnect aborts 3 times before success")
    void multipleAbortedReconnectsCanSucceed(int teacherStart, int teacherEnd, int learnerStart, int learnerEnd) {
        for (int i = teacherStart; i < teacherEnd; i++) {
            teacherMap.put(new TestKey(i), new TestValue(i));
        }

        for (int i = learnerStart; i < learnerEnd; i++) {
            learnerMap.put(new TestKey(i), new TestValue(i));
        }

        learnerBuilder.setNumCallsBeforeThrow((teacherEnd - teacherStart) / 2);
        learnerBuilder.setNumTimesToBreak(2);

        reconnectMultipleTimes(3);
    }

    static Stream<Arguments> provideLargeTreePermutations() {
        final List<Arguments> args = new ArrayList<>();
        // Two million leaf trees that have no intersection
        args.add(Arguments.of(0, 1_000_000, 1_000_000, 2_000_000));
        // Two million leaf trees that intersect
        args.add(Arguments.of(0, 1_000_000, 500_000, 1_500_000));
        // A smaller tree and larger tree that do not intersect
        args.add(Arguments.of(0, 10_000, 1_000_000, 2_000_000));
        args.add(Arguments.of(1_000_000, 2_000_000, 0, 10_000));
        // A smaller tree and larger tree that do intersect
        args.add(Arguments.of(0, 10_000, 5_000, 1_005_000));
        args.add(Arguments.of(5_000, 1_005_000, 0, 10_000));

        // Two million leaf trees that intersect
        args.add(Arguments.of(50_000, 250_000, 0, 100_000));
        args.add(Arguments.of(50_000, 249_999, 0, 100_000));
        args.add(Arguments.of(50_000, 250_001, 0, 100_000));
        return args.stream();
    }
}
