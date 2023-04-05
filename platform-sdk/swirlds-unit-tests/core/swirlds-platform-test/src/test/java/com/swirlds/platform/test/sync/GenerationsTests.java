/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.sync.Generations;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class GenerationsTests {

    private static Stream<Arguments> invalidGenerations() {
        return Stream.of(
                Arguments.of(-1, 0, 0),
                Arguments.of(0, -1, 0),
                Arguments.of(0, 0, -1),
                Arguments.of(1, 0, 0),
                Arguments.of(0, 1, 0));
    }

    private static Stream<Arguments> validGenerations() {
        return Stream.of(Arguments.of(0, 0, 0), Arguments.of(1, 1, 1), Arguments.of(1, 2, 2), Arguments.of(1, 2, 3));
    }

    @ParameterizedTest
    @MethodSource("invalidGenerations")
    void testConstructor_InvalidGenerations(
            final long minRoundGen, final long minNonAncientGen, final long maxRoundGen) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Generations(minRoundGen, minNonAncientGen, maxRoundGen),
                "Invalid constructor params should result in an exception.");
    }

    @ParameterizedTest
    @MethodSource("validGenerations")
    void testConstructor_ValidGenerations(final long minRoundGen, final long minNonAncientGen, final long maxRoundGen) {
        assertDoesNotThrow(
                () -> new Generations(minRoundGen, minNonAncientGen, maxRoundGen),
                "Valid constructor params should not result in an exception.");
    }

    @Test
    void testDefaultMethods() {
        final long generationDiff = 100;
        final GraphGenerations genesisGenerations = new Generations(
                GraphGenerations.FIRST_GENERATION,
                GraphGenerations.FIRST_GENERATION,
                GraphGenerations.FIRST_GENERATION);
        assertFalse(genesisGenerations.areAnyEventsAncient(), "at genesis, no events should be ancient");

        final long randomGeneration = Math.abs(new Random().nextLong());
        final PlatformEvent e = Mockito.mock(PlatformEvent.class);
        Mockito.when(e.getGeneration()).thenReturn(randomGeneration);

        assertFalse(genesisGenerations.isAncient(e), "at genesis, no events should be ancient");

        final GraphGenerations randomNonAncient =
                new Generations(randomGeneration - generationDiff, randomGeneration, randomGeneration + generationDiff);

        final PlatformEvent newer = Mockito.mock(PlatformEvent.class);
        Mockito.when(newer.getGeneration()).thenReturn(randomGeneration + 1);
        final PlatformEvent older = Mockito.mock(PlatformEvent.class);
        Mockito.when(older.getGeneration()).thenReturn(randomGeneration - 1);

        assertFalse(
                randomNonAncient.isAncient(e),
                "if an events generation is equal to minNonAncientGen, it should be non-ancient");
        assertFalse(
                randomNonAncient.isAncient(newer),
                "if an events generation is higher to minNonAncientGen, it should be non-ancient");
        assertTrue(
                randomNonAncient.isAncient(older),
                "if an events generation is lower to minNonAncientGen, it should be ancient");
    }
}
