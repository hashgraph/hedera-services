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

package com.swirlds.platform.test.event.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.EventDeduplication;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.event.validation.GossipEventValidator;
import com.swirlds.platform.event.validation.GossipEventValidators;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.event.validation.TransactionSizeValidator;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.test.event.GossipEventBuilder;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class EventValidatorTests {
    private static final GossipEventValidator VALID = (e) -> true;
    private static final GossipEventValidator INVALID = (e) -> false;

    private static GossipEvent eventWithParents(
            final long selfParentGen,
            final long otherParentGen,
            final Hash selfParentHash,
            final Hash otherParentHash) {
        final GossipEvent event = mock(GossipEvent.class);
        final long creatorId = 0;
        final Instant timeCreated = Instant.now();
        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[0];
        Mockito.when(event.getHashedData())
                .thenReturn(new BaseEventHashedData(
                        new BasicSoftwareVersion(1),
                        new NodeId(creatorId),
                        selfParentGen,
                        otherParentGen,
                        selfParentHash,
                        otherParentHash,
                        timeCreated,
                        transactions));
        return event;
    }

    @Test
    void baseEventValidators() {
        final GossipEvent event = GossipEventBuilder.builder().buildEvent();
        assertTrue(new GossipEventValidators(List.of()).isEventValid(event));
        assertTrue(new GossipEventValidators(List.of(VALID)).isEventValid(event));
        assertTrue(new GossipEventValidators(List.of(VALID, VALID, VALID)).isEventValid(event));
        assertFalse(new GossipEventValidators(List.of(VALID, INVALID, VALID)).isEventValid(event));
    }

    @Test
    void replaceByName() {
        final GossipEventValidator valid1 = new TestGossipEventValidator(true, "1");
        final GossipEventValidator invalid1 = new TestGossipEventValidator(false, "1");
        final GossipEventValidator valid2 = new TestGossipEventValidator(true, "2");
        final GossipEventValidator invalid2 = new TestGossipEventValidator(false, "2");

        final GossipEvent event = GossipEventBuilder.builder().buildEvent();
        final GossipEventValidators validators = new GossipEventValidators(List.of(invalid1, invalid2));

        assertFalse(validators.isEventValid(event));
        validators.replaceValidator("1", valid1);
        assertFalse(validators.isEventValid(event));
        validators.replaceValidator("2", valid2);
        assertTrue(validators.isEventValid(event));

        assertThrows(IllegalArgumentException.class, () -> validators.replaceValidator("3", valid1));
    }

    @Test
    void eventValidator() {
        final Set<GossipEvent> intakeEvents = new HashSet<>();
        final AtomicBoolean isValid = new AtomicBoolean(true);
        final EventValidator eventValidator = new EventValidator(
                (e) -> isValid.get(), intakeEvents::add, mock(PhaseTimer.class), mock(IntakeEventCounter.class));

        final GossipEvent validEvent = GossipEventBuilder.builder().buildEvent();
        eventValidator.validateEvent(validEvent);
        assertTrue(intakeEvents.contains(validEvent), "event should have been passed to intake");

        isValid.set(false);
        final GossipEvent invalidEvent = GossipEventBuilder.builder().buildEvent();
        eventValidator.validateEvent(invalidEvent);
        assertFalse(intakeEvents.contains(invalidEvent), "event should not have been passed to intake");
    }

    @Test
    void eventDeduplication() {
        final AtomicBoolean isDuplicate = new AtomicBoolean(true);
        final EventIntakeMetrics metrics = mock(EventIntakeMetrics.class);
        final EventDeduplication deduplication = new EventDeduplication(e -> isDuplicate.get(), metrics);
        final GossipEvent event = GossipEventBuilder.builder().buildEvent();

        assertFalse(deduplication.isEventValid(event), "it should be a duplicate, so not valid");
        verify(metrics, description("metrics should have recorded the duplicate event"))
                .duplicateEvent();
        verify(metrics, never().description("there was no non-duplicate event so far"))
                .nonDuplicateEvent();

        isDuplicate.set(false);

        assertTrue(deduplication.isEventValid(event), "it should not be a duplicate, so valid");
        verify(metrics, description("metrics should have recorded the duplicate event"))
                .duplicateEvent();
        verify(metrics, description("metrics should have recorded the non-duplicate event"))
                .nonDuplicateEvent();
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1999, 2000, 2001, 10_000})
    void accumulatedTransactionSize(final int transAmount) {
        final int maxTransactionBytesPerEvent = 2000;
        final GossipEventValidator validator = new TransactionSizeValidator(maxTransactionBytesPerEvent);
        final GossipEvent event = GossipEventBuilder.builder()
                .setNumberOfTransactions(transAmount)
                .buildEvent();

        int eventTransSize = 0;
        for (final Transaction t : event.getHashedData().getTransactions()) {
            eventTransSize += t.getSerializedLength();
        }
        if (eventTransSize <= maxTransactionBytesPerEvent) {
            assertTrue(validator.isEventValid(event), "transaction limit should not have been exceeded");
        } else {
            assertFalse(validator.isEventValid(event), "transaction limit should have been exceeded");
        }
    }

    @Test
    void parentValidity() {
        final long undefinedGeneration = EventConstants.GENERATION_UNDEFINED;
        final long validGeneration = 10;
        final Hash hash1 = RandomUtils.randomHash();
        final Hash hash2 = RandomUtils.randomHash();
        final Hash nullHash = null;

        final GossipEventValidator validator = StaticValidators.buildParentValidator(10);

        // has generation but no hash
        assertFalse(validator.isEventValid(eventWithParents(validGeneration, undefinedGeneration, nullHash, nullHash)));
        assertFalse(validator.isEventValid(eventWithParents(undefinedGeneration, validGeneration, nullHash, nullHash)));

        // has hash but no generation
        assertFalse(
                validator.isEventValid(eventWithParents(undefinedGeneration, undefinedGeneration, hash1, nullHash)));
        assertFalse(
                validator.isEventValid(eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, hash2)));

        // both parents same hash
        assertFalse(validator.isEventValid(eventWithParents(validGeneration, validGeneration, hash1, hash1)));

        // no parents
        assertTrue(
                validator.isEventValid(eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, nullHash)));

        // valid parents
        assertTrue(validator.isEventValid(eventWithParents(validGeneration, undefinedGeneration, hash1, nullHash)));
        assertTrue(validator.isEventValid(eventWithParents(undefinedGeneration, validGeneration, nullHash, hash2)));
        assertTrue(validator.isEventValid(eventWithParents(validGeneration, validGeneration, hash1, hash2)));
    }

    @Test
    void parentValiditySizeOneNetwork() {
        final long undefinedGeneration = EventConstants.GENERATION_UNDEFINED;
        final long validGeneration = 10;
        final Hash hash1 = RandomUtils.randomHash();
        final Hash hash2 = RandomUtils.randomHash();
        final Hash nullHash = null;

        final GossipEventValidator validator = StaticValidators.buildParentValidator(1);

        // has generation but no hash
        assertFalse(validator.isEventValid(eventWithParents(validGeneration, undefinedGeneration, nullHash, nullHash)));
        assertFalse(validator.isEventValid(eventWithParents(undefinedGeneration, validGeneration, nullHash, nullHash)));

        // has hash but no generation
        assertFalse(
                validator.isEventValid(eventWithParents(undefinedGeneration, undefinedGeneration, hash1, nullHash)));
        assertFalse(
                validator.isEventValid(eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, hash2)));

        // both parents same hash
        assertTrue(validator.isEventValid(eventWithParents(validGeneration, validGeneration, hash1, hash1)));

        // no parents
        assertTrue(
                validator.isEventValid(eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, nullHash)));

        // valid parents
        assertTrue(validator.isEventValid(eventWithParents(validGeneration, undefinedGeneration, hash1, nullHash)));
        assertTrue(validator.isEventValid(eventWithParents(undefinedGeneration, validGeneration, nullHash, hash2)));
        assertTrue(validator.isEventValid(eventWithParents(validGeneration, validGeneration, hash1, hash2)));
    }

    @Test
    void invalidCreationTime() {
        final Instant time = Instant.now();
        final GossipEvent gossipEvent =
                GossipEventBuilder.builder().setTimestamp(time).buildEvent();
        final EventImpl event = new EventImpl(gossipEvent, null, null);

        event.setSelfParent(new EventImpl(
                GossipEventBuilder.builder().setTimestamp(time.plusNanos(100)).buildEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");

        event.setSelfParent(new EventImpl(
                GossipEventBuilder.builder().setTimestamp(time.plusNanos(1)).buildEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");

        event.setSelfParent(
                new EventImpl(GossipEventBuilder.builder().setTimestamp(time).buildEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");
    }
}
