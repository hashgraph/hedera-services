/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.RandomUtils.randomPositiveLong;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.MinGenInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventRecoveryWorkflowTests {

    @TempDir
    Path tmpDir;

    @Test
    @DisplayName("getMinGenInfo() Test")
    void getMinGenInfoTest() {

        // FUTURE WORK: recovery currently does not rebuild minimum generations the way we want to (due to lack
        //  of data in the event stream). This test validates current behavior, not the behavior we eventually
        //  want. Once we have all the data in the event stream and minimum generation behavior in recovery is
        //  fixed, we should throw this test away and write a new test that validates the new behavior.

        final int roundsToSimulate = 50;
        final int roundsNonAncient = 26;

        final Map<Long /* round */, Long /* min gen */> minGenForRound = new HashMap<>();
        List<MinGenInfo> minGenInfos = new ArrayList<>();

        long roundNumber = 1;

        // Generate some initial rounds
        for (long i = 0; i < roundsNonAncient; i++) {
            minGenForRound.put(roundNumber, roundNumber);
            minGenInfos.add(new MinGenInfo(roundNumber, roundNumber));
            roundNumber++;
        }

        // Simulate further rounds.
        for (long i = 0; i < roundsToSimulate; i++) {
            final long minimumGeneration = i * 2;
            final long nextMinimumGeneration = (i + 1) * 2;
            minGenForRound.put(roundNumber, minimumGeneration);

            // Create mock events for the round
            final List<ConsensusEvent> events = new ArrayList<>();
            for (long generation = minimumGeneration; generation < nextMinimumGeneration; generation++) {
                final EventImpl event = mock(EventImpl.class);
                when(event.getGeneration()).thenReturn(generation);
                events.add(event);
            }
            Collections.shuffle(events);

            // Create a mock round
            final Round round = mock(Round.class);
            when(round.isEmpty()).thenReturn(false);
            when(round.getRoundNum()).thenReturn(roundNumber);
            when(round.iterator()).thenReturn(events.iterator());

            minGenInfos = EventRecoveryWorkflow.getMinGenInfo(roundsNonAncient, minGenInfos, round);

            assertEquals(roundsNonAncient + 1, minGenInfos.size(), "unexpected number of min gen infos");

            long expectedRound = roundNumber - roundsNonAncient;
            for (final MinGenInfo minGenInfo : minGenInfos) {
                assertEquals(expectedRound, minGenInfo.round(), "unexpected round");
                assertEquals(minGenForRound.get(expectedRound), minGenInfo.minimumGeneration(), "unexpected round");
                expectedRound++;
            }

            roundNumber++;
        }
    }

    @Test
    @DisplayName("Collect Events For Round Test")
    void collectEventsForRoundTest() {

        // FUTURE WORK: this test can be removed once events are removed from the state.

        final int roundsToSimulate = 50;
        final int roundsNonAncient = 26;
        final int eventsPerRound = 10;

        final List<EventImpl> expectedEvents = new LinkedList<>();

        long roundNumber = 1;

        // Generate some initial rounds
        for (long i = 0; i < roundsNonAncient + 1; i++) {
            for (int eventIndex = 0; eventIndex < eventsPerRound; eventIndex++) {
                final EventImpl event = mock(EventImpl.class);
                when(event.getRoundReceived()).thenReturn(roundNumber);
                expectedEvents.add(event);
            }
            roundNumber++;
        }

        // Simulate further rounds.
        for (long i = 0; i < roundsToSimulate; i++) {

            final EventImpl[] previousEvents = new EventImpl[expectedEvents.size()];
            for (int index = 0; index < expectedEvents.size(); index++) {
                previousEvents[index] = expectedEvents.get(index);
            }

            // Generate new events
            final List<ConsensusEvent> newEvents = new ArrayList<>();
            for (int eventIndex = 0; eventIndex < eventsPerRound; eventIndex++) {
                final EventImpl event = mock(EventImpl.class);
                when(event.getRoundReceived()).thenReturn(roundNumber);
                newEvents.add(event);
                expectedEvents.add(event);
            }

            // Remove old events
            final long roundToRemove = expectedEvents.get(0).getRoundReceived();
            final Iterator<EventImpl> iterator = expectedEvents.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getRoundReceived() == roundToRemove) {
                    iterator.remove();
                } else {
                    break;
                }
            }

            final Round round = mock(Round.class);
            when(round.getRoundNum()).thenReturn(roundNumber);
            when(round.iterator()).thenReturn(newEvents.iterator());

            final EventImpl[] eventsForState =
                    EventRecoveryWorkflow.collectEventsForRound(roundsNonAncient, previousEvents, round);

            assertArrayEquals(expectedEvents.toArray(), eventsForState, "unexpected events");

            roundNumber++;
        }
    }

    @Test
    @DisplayName("isFreezeState() Test")
    void isFreezeStateTest() {

        // no freeze time
        assertFalse(
                EventRecoveryWorkflow.isFreezeState(Instant.ofEpochSecond(0), Instant.ofEpochSecond(0), null),
                "unexpected freeze behavior");

        // previous before, current before
        assertFalse(
                EventRecoveryWorkflow.isFreezeState(
                        Instant.ofEpochSecond(0), Instant.ofEpochSecond(1), Instant.ofEpochSecond(100)),
                "unexpected freeze behavior");

        // previous before, current equal
        assertTrue(
                EventRecoveryWorkflow.isFreezeState(
                        Instant.ofEpochSecond(0), Instant.ofEpochSecond(100), Instant.ofEpochSecond(100)),
                "unexpected freeze behavior");

        // previous before, current after
        assertTrue(
                EventRecoveryWorkflow.isFreezeState(
                        Instant.ofEpochSecond(0), Instant.ofEpochSecond(101), Instant.ofEpochSecond(100)),
                "unexpected freeze behavior");

        // previous equal, current after
        assertFalse(
                EventRecoveryWorkflow.isFreezeState(
                        Instant.ofEpochSecond(100), Instant.ofEpochSecond(101), Instant.ofEpochSecond(100)),
                "unexpected freeze behavior");

        // previous after, current after
        assertFalse(
                EventRecoveryWorkflow.isFreezeState(
                        Instant.ofEpochSecond(101), Instant.ofEpochSecond(102), Instant.ofEpochSecond(100)),
                "unexpected freeze behavior");
    }

    @Test
    @DisplayName("applyTransactions() Test")
    void applyTransactionsTest() {
        final SwirldDualState dualState = mock(SwirldDualState.class);

        final List<ConsensusEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(mock(EventImpl.class));
        }

        final Round round = mock(Round.class);
        when(round.iterator()).thenReturn(events.iterator());

        final List<EventImpl> preHandleList = new ArrayList<>();
        final AtomicBoolean roundHandled = new AtomicBoolean(false);

        final SwirldState immutableState = mock(SwirldState.class);
        doAnswer(invocation -> {
                    assertFalse(roundHandled.get(), "round should not have been handled yet");
                    preHandleList.add(invocation.getArgument(0));
                    return null;
                })
                .when(immutableState)
                .preHandle(any());
        doAnswer(invocation -> {
                    fail("mutable state should handle transactions");
                    return null;
                })
                .when(immutableState)
                .handleConsensusRound(any(), any());

        final SwirldState mutableState = mock(SwirldState.class);
        doAnswer(invocation -> {
                    fail("immutable state should pre-handle transactions");
                    return null;
                })
                .when(mutableState)
                .preHandle(any());
        doAnswer(invocation -> {
                    assertFalse(roundHandled.get(), "round should only be handled once");
                    assertSame(round, invocation.getArgument(0), "unexpected round");
                    assertSame(dualState, invocation.getArgument(1), "unexpected dual state");
                    roundHandled.set(true);
                    return null;
                })
                .when(mutableState)
                .handleConsensusRound(any(), any());

        EventRecoveryWorkflow.applyTransactions(immutableState, mutableState, dualState, round);

        assertEquals(events.size(), preHandleList.size(), "incorrect number of pre-handle calls");
        for (int index = 0; index < events.size(); index++) {
            assertSame(events.get(index), preHandleList.get(index));
        }
        assertTrue(roundHandled.get(), "round not handled");
    }

    @Test
    @DisplayName("getRoundTimestamp() Test")
    void getRoundTimestampTest() {
        final int eventCount = 100;

        final List<ConsensusEvent> events = new ArrayList<>();
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            final EventImpl event = mock(EventImpl.class);
            when(event.getConsensusTimestamp()).thenReturn(Instant.ofEpochSecond(eventIndex));
            events.add(event);
        }

        final Round round = mock(Round.class);
        when(round.iterator()).thenReturn(events.iterator());

        final Instant roundTimestamp = EventRecoveryWorkflow.getRoundTimestamp(round);

        assertEquals(Instant.ofEpochSecond(eventCount - 1), roundTimestamp, "unexpected timestamp");
    }

    private EventImpl buildEventWithRunningHash(final Hash eventHash) {
        final EventImpl event = mock(EventImpl.class);
        when(event.getHash()).thenReturn(eventHash);
        final RunningHash runningHash = new RunningHash();
        when(event.getRunningHash()).thenReturn(runningHash);
        return event;
    }

    /**
     * The running hash implementation is quite bad -- hashing the same object twice is not deterministic since hashing
     * leaves behind metadata. To work around this, this method creates a fresh copy of an event list that does not
     * share a metadata link.
     */
    private List<ConsensusEvent> copyRunningHashEvents(final List<ConsensusEvent> original) {
        final List<ConsensusEvent> copy = new ArrayList<>();
        original.forEach(event -> copy.add(buildEventWithRunningHash((((EventImpl) event).getHash()))));
        return copy;
    }

    private Round buildMockRound(final List<ConsensusEvent> events) {
        final Round round = mock(Round.class);
        when(round.iterator()).thenReturn(events.iterator());
        return round;
    }

    @Test
    @DisplayName("getHashEventConsTest")
    void getHashEventConsTest() {
        final Random random = getRandomPrintSeed();
        final int eventCount = 100;

        final Hash initialHash1 = randomHash(random);

        final List<ConsensusEvent> events1 = new ArrayList<>();
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            final EventImpl event = buildEventWithRunningHash(randomHash(random));
            events1.add(event);
        }
        final Hash hash1 = EventRecoveryWorkflow.getHashEventsCons(initialHash1, buildMockRound(events1));

        // Hash should be deterministic
        assertEquals(
                hash1,
                EventRecoveryWorkflow.getHashEventsCons(initialHash1, buildMockRound(copyRunningHashEvents(events1))),
                "hash should be deterministic");

        // Different starting hash
        final Hash initialHash2 = randomHash(random);
        assertNotEquals(
                hash1,
                EventRecoveryWorkflow.getHashEventsCons(initialHash2, buildMockRound(copyRunningHashEvents(events1))),
                "hash should have changed");

        // add another event
        final List<ConsensusEvent> events2 = copyRunningHashEvents(events1);
        final EventImpl newEvent = buildEventWithRunningHash(randomHash(random));
        events2.add(newEvent);
        assertNotEquals(
                hash1,
                EventRecoveryWorkflow.getHashEventsCons(initialHash1, buildMockRound(events2)),
                "hash should have changed");

        // remove an event
        final List<ConsensusEvent> events3 = copyRunningHashEvents(events1);
        events3.remove(events3.size() - 1);
        assertNotEquals(
                hash1,
                EventRecoveryWorkflow.getHashEventsCons(initialHash1, buildMockRound(events3)),
                "hash should have changed");

        // replace an event
        final List<ConsensusEvent> events4 = copyRunningHashEvents(events1);
        final EventImpl replacementEvent = buildEventWithRunningHash(randomHash(random));
        events4.set(0, replacementEvent);
        assertNotEquals(
                hash1,
                EventRecoveryWorkflow.getHashEventsCons(initialHash1, buildMockRound(events4)),
                "hash should have changed");
    }

    @Test
    void testUpdateEmergencyRecoveryFile() throws IOException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final Hash hash = randomHash(random);
        final long round = randomPositiveLong(random);
        final Instant stateTimestamp = Instant.ofEpochMilli(randomPositiveLong(random));

        final EmergencyRecoveryFile recoveryFile = new EmergencyRecoveryFile(round, hash, stateTimestamp);
        recoveryFile.write(tmpDir);

        final Instant bootstrapTime = Instant.ofEpochMilli(randomPositiveLong(random));

        EventRecoveryWorkflow.updateEmergencyRecoveryFile(tmpDir, bootstrapTime);

        // Verify the contents of the updated recovery file
        final EmergencyRecoveryFile updatedRecoveryFile = EmergencyRecoveryFile.read(tmpDir);
        assertNotNull(updatedRecoveryFile, "Updated recovery file should not be null");
        assertEquals(round, updatedRecoveryFile.round(), "round does not match");
        assertEquals(hash, updatedRecoveryFile.hash(), "hash does not match");
        assertEquals(stateTimestamp, updatedRecoveryFile.timestamp(), "state timestamp does not match");
        assertNotNull(updatedRecoveryFile.recovery().boostrap(), "bootstrap should not be null");
        assertEquals(
                bootstrapTime,
                updatedRecoveryFile.recovery().boostrap().timestamp(),
                "bootstrap timestamp does not match");

        // Verify the contents of the backup recovery file (copy of the original)
        final EmergencyRecoveryFile backupFile = EmergencyRecoveryFile.read(tmpDir.resolve("backup"));
        assertNotNull(backupFile, "Updated recovery file should not be null");
        assertEquals(round, backupFile.round(), "round does not match");
        assertEquals(hash, backupFile.hash(), "hash does not match");
        assertEquals(stateTimestamp, backupFile.timestamp(), "state timestamp does not match");
        assertNull(backupFile.recovery().boostrap(), "No bootstrap information should exist in the backup");
    }

    // FUTURE WORK reapplyTransactions() test
    // FUTURE WORK recoverState() test
}
