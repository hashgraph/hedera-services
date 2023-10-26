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

package com.swirlds.platform.test.event;

import static com.swirlds.platform.event.EventUtils.checkForGenerationGaps;
import static com.swirlds.platform.event.EventUtils.prepareForShadowGraph;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.internal.EventImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventUtilsTests {

    private final EventImpl gen1 = mock(EventImpl.class);
    private final EventImpl gen2 = mock(EventImpl.class);
    private final EventImpl gen3 = mock(EventImpl.class);
    private final EventImpl gen4 = mock(EventImpl.class);
    private final EventImpl gen5 = mock(EventImpl.class);
    private final EventImpl gen6 = mock(EventImpl.class);
    private final EventImpl gen7 = mock(EventImpl.class);

    @BeforeEach
    void setup() {
        when(gen1.getGeneration()).thenReturn(1L);
        when(gen2.getGeneration()).thenReturn(2L);
        when(gen3.getGeneration()).thenReturn(3L);
        when(gen4.getGeneration()).thenReturn(4L);
        when(gen5.getGeneration()).thenReturn(5L);
        when(gen6.getGeneration()).thenReturn(6L);
        when(gen7.getGeneration()).thenReturn(7L);

        when(gen1.getRoundCreated()).thenReturn(1L);
        when(gen2.getRoundCreated()).thenReturn(2L);
        when(gen3.getRoundCreated()).thenReturn(3L);
        when(gen4.getRoundCreated()).thenReturn(4L);
        when(gen5.getRoundCreated()).thenReturn(5L);
        when(gen6.getRoundCreated()).thenReturn(6L);
        when(gen7.getRoundCreated()).thenReturn(7L);
    }

    @Test
    void testPrepareForShadowGraph_NullEvents() {
        assertTrue(prepareForShadowGraph(null).isEmpty(), "Passing a null event array should return an empty list.");
    }

    @Test
    void testPrepareForShadowGraph() {
        EventImpl[] events = new EventImpl[4];
        events[0] = gen5;
        events[1] = gen2;
        events[2] = gen4;
        events[3] = gen3;

        List<EventImpl> sorted = prepareForShadowGraph(events);

        String msg = "The returned list should be sorted.";
        assertEquals(gen2, sorted.get(0), msg);
        assertEquals(gen3, sorted.get(1), msg);
        assertEquals(gen4, sorted.get(2), msg);
        assertEquals(gen5, sorted.get(3), msg);
    }

    @Test
    void testPrepareForShadowGraph_EmptyEvents() {
        assertTrue(
                prepareForShadowGraph(new EventImpl[0]).isEmpty(),
                "Passing a empty event array should return an empty list.");
    }

    @Test
    void testCheckForGenerationGaps_NullList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> checkForGenerationGaps(null),
                "Null event list should throw an exception.");
    }

    @Test
    void testCheckForGenerationGaps_EmptyList() {
        final List<EventImpl> list = Collections.emptyList();
        assertThrows(
                IllegalArgumentException.class,
                () -> checkForGenerationGaps(list),
                "Empty event list should throw an exception.");
    }

    @Test
    void testCheckForGenerationGaps_NoGaps() {
        final List<EventImpl> list = List.of(gen1, gen2, gen3, gen4, gen5, gen6, gen7);
        assertDoesNotThrow(() -> checkForGenerationGaps(list), "There are no gaps, so no exception should be thrown");
    }

    @Test
    void testCheckForGenerationGaps_OneEvent() {
        final List<EventImpl> list = List.of(gen1);
        assertDoesNotThrow(() -> checkForGenerationGaps(list), "There can be no gap if there is only 1 event");
    }

    @Test
    @DisplayName("A single generation gap of 1 generation")
    void testCheckForGenerationGaps_OneSmallGap() {
        final List<EventImpl> list = List.of(gen1, gen2, gen4, gen5, gen6, gen7);
        assertThrows(
                IllegalArgumentException.class,
                () -> checkForGenerationGaps(list),
                "A list with a gap should throw an exception.");
    }

    @Test
    @DisplayName("A single generation gap of multiple generations")
    void testCheckForGenerationGaps_OneBigGap() {
        final List<EventImpl> list = List.of(gen1, gen5, gen6, gen7);
        assertThrows(
                IllegalArgumentException.class,
                () -> checkForGenerationGaps(list),
                "A list with a gap should throw an exception.");
    }

    @Test
    @DisplayName("Multiple generation gaps of 1 generation")
    void testCheckForGenerationGaps_TwoGaps() {
        final List<EventImpl> list = Arrays.asList(gen1, gen2, gen4, gen6, gen7);
        assertThrows(
                IllegalArgumentException.class,
                () -> checkForGenerationGaps(list),
                "A list with a gap should throw an exception.");
    }
}
