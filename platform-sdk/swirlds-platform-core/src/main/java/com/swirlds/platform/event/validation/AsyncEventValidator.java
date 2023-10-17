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

package com.swirlds.platform.event.validation;

import static com.swirlds.platform.event.validation.EventValidationChecks.areParentsValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isSignatureValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isValidTimeCreated;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Threadsafe validator for events.
 */
public class AsyncEventValidator {
    /**
     * Valid events are passed to this consumer.
     */
    private final Consumer<GossipEvent> eventConsumer;

    /**
     * A map from node ID to public key, generated from the previous address book.
     * <p>
     * The value of this field will be an empty map if there is no previous address book.
     */
    private final AtomicReference<Map<NodeId, PublicKey>> previousKeyMap = new AtomicReference<>();

    /**
     * A map from node ID to public key, generated from the current address book.
     * <p>
     * The value of this field will never be null, since there is always a current address book
     */
    private final AtomicReference<Map<NodeId, PublicKey>> currentKeyMap = new AtomicReference<>();

    /**
     * The current software version.
     */
    private final AtomicReference<SoftwareVersion> currentSoftwareVersion = new AtomicReference<>();

    /**
     * The current minimum generation required for an event to be non-ancient.
     */
    private final AtomicLong minimumGenerationNonAncient = new AtomicLong(0);

    /**
     * Keeps track of the number of events in the intake pipeline from each peer
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * True if this node is in a single-node network, otherwise false.
     * <p>
     * This value is not updated when the address book is updated: this is a test-only feature, and changing from
     * single-node to multi-node or vice versa is not supported.
     */
    private final boolean singleNodeNetwork;

    //    private static final LongAccumulator.Config DUPLICATE_EVENT_CONFIG = new LongAccumulator.Config(
    //            PLATFORM_CATEGORY, "duplicateEvents")
    //            .withDescription("Events received that exactly match a previous event")
    //            .withUnit("events");
    //    private final LongAccumulator duplicateEventAccumulator;

    /**
     * Constructor
     *
     * @param platformContext     the platform context
     * @param eventConsumer       deduplicated events are passed to this consumer
     * @param previousAddressBook the previous address book
     * @param currentAddressBook  the current address book
     * @param intakeEventCounter  keeps track of the number of events in the intake pipeline from each peer
     */
    public AsyncEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        this.eventConsumer = Objects.requireNonNull(eventConsumer);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.singleNodeNetwork = currentAddressBook.getSize() == 1;

        setCurrentAddressBook(currentAddressBook);

        if (previousAddressBook != null) {
            setPreviousAddressBook(previousAddressBook);
        } else {
            previousKeyMap.set(new HashMap<>());
        }
    }

    /**
     * Validate an event.
     * <p>
     * If the event is determined to be valid, it is passed to the event consumer.
     * <p>
     * This method is threadsafe, and may be called concurrently from multiple threads.
     *
     * @param event the event to validate
     */
    public void handleEvent(@NonNull final EventImpl event) {
        if (event.getGeneration() >= minimumGenerationNonAncient.get()
                && isValidTimeCreated(event)
                && areParentsValid(event, singleNodeNetwork)
                && isSignatureValid(
                        event.getBaseEvent(),
                        currentSoftwareVersion.get(),
                        previousKeyMap.get(),
                        currentKeyMap.get())) {

            eventConsumer.accept(event.getBaseEvent());
        } else {
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Set the minimum generation required for an event to be non-ancient.
     *
     * @param minimumGenerationNonAncient the minimum generation required for an event to be non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        this.minimumGenerationNonAncient.set(minimumGenerationNonAncient);
    }

    /**
     * Set the previous address book.
     *
     * @param addressBook the previous address book
     */
    public void setPreviousAddressBook(@NonNull final AddressBook addressBook) {
        final Map<NodeId, PublicKey> keyMap = new HashMap<>();
        addressBook.forEach(address -> keyMap.put(address.getNodeId(), address.getSigPublicKey()));

        this.previousKeyMap.set(keyMap);
    }

    /**
     * Set the current address book.
     *
     * @param addressBook the current address book
     */
    public void setCurrentAddressBook(@NonNull final AddressBook addressBook) {
        final Map<NodeId, PublicKey> keyMap = new HashMap<>();
        addressBook.forEach(address -> keyMap.put(address.getNodeId(), address.getSigPublicKey()));

        this.currentKeyMap.set(keyMap);
    }

    /**
     * Set the current software version.
     *
     * @param softwareVersion the current software version
     */
    public void setCurrentSoftwareVersion(@NonNull final SoftwareVersion softwareVersion) {
        this.currentSoftwareVersion.set(softwareVersion);
    }
}
