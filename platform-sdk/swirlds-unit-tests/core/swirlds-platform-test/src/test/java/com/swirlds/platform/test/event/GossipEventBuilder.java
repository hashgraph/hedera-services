/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;

public class GossipEventBuilder {
    private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochMilli(1588771316678L);
    private Random random;
    private NodeId creatorId;
    private Instant timestamp;
    private int numberOfTransactions;
    private int transactionSize;
    private Object selfParent;
    private Object otherParent;
    private boolean fakeHash;
    private long fakeGeneration;

    private boolean consensus;

    public static GossipEventBuilder builder() {
        return new GossipEventBuilder().setDefaults();
    }

    public GossipEventBuilder setDefaults() {
        random = new Random();
        creatorId = new NodeId(0);
        timestamp = null;
        numberOfTransactions = 0;
        transactionSize = 4;
        selfParent = null;
        otherParent = null;
        fakeHash = true;
        fakeGeneration = Long.MIN_VALUE;
        consensus = false;
        return this;
    }

    public GossipEventBuilder setRandom(final Random random) {
        this.random = random;
        return this;
    }

    public GossipEventBuilder setCreatorId(final long creatorId) {
        this.creatorId = new NodeId(creatorId);
        return this;
    }

    public GossipEventBuilder setCreatorId(final NodeId creatorId) {
        this.creatorId = creatorId;
        return this;
    }

    public GossipEventBuilder setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public GossipEventBuilder setNumberOfTransactions(final int numberOfTransactions) {
        this.numberOfTransactions = numberOfTransactions;
        return this;
    }

    public GossipEventBuilder setTransactionSize(final int transactionSize) {
        this.transactionSize = transactionSize;
        return this;
    }

    public GossipEventBuilder setSelfParent(final GossipEvent selfParent) {
        this.selfParent = selfParent;
        return this;
    }

    public GossipEventBuilder setOtherParent(final GossipEvent otherParent) {
        this.otherParent = otherParent;
        return this;
    }

    public GossipEventBuilder setSelfParent(final EventImpl selfParent) {
        this.selfParent = selfParent;
        return this;
    }

    public GossipEventBuilder setOtherParent(final EventImpl otherParent) {
        this.otherParent = otherParent;
        return this;
    }

    public GossipEventBuilder setFakeHash(final boolean fakeHash) {
        this.fakeHash = fakeHash;
        return this;
    }

    public GossipEventBuilder setGeneration(final long generation) {
        fakeGeneration = generation;
        return this;
    }

    public GossipEventBuilder setConsensus(final boolean consensus) {
        this.consensus = consensus;
        return this;
    }

    private EventImpl getSelfParentImpl() {
        return selfParent instanceof EventImpl ei ? ei : null;
    }

    private EventImpl getOtherParentImpl() {
        return otherParent instanceof EventImpl ei ? ei : null;
    }

    private GossipEvent getSelfParentGossip() {
        if (selfParent instanceof GossipEvent ge) {
            return ge;
        }
        return selfParent instanceof EventImpl ei ? ei.getBaseEvent() : null;
    }

    private GossipEvent getOtherParentGossip() {
        if (otherParent instanceof GossipEvent ge) {
            return ge;
        }
        return otherParent instanceof EventImpl ei ? ei.getBaseEvent() : null;
    }

    private Instant getParentTime() {
        final Instant sp = getSelfParentGossip() == null
                ? DEFAULT_TIMESTAMP
                : getSelfParentGossip().getHashedData().getTimeCreated();
        final Instant op = getOtherParentGossip() == null
                ? DEFAULT_TIMESTAMP
                : getOtherParentGossip().getHashedData().getTimeCreated();
        return sp.isAfter(op) ? sp : op;
    }

    public GossipEvent buildEvent() {
        return buildGossipEvent();
    }

    public GossipEvent buildGossipEvent() {
        final ConsensusTransactionImpl[] tr = new ConsensusTransactionImpl[numberOfTransactions];
        for (int i = 0; i < tr.length; ++i) {
            final byte[] bytes = new byte[] {(byte) i, (byte) i, (byte) i, (byte) i};
            tr[i] = new SwirldTransaction(bytes);
        }
        final long selfParentGen = fakeGeneration >= GraphGenerations.FIRST_GENERATION
                ? fakeGeneration - 1
                : getSelfParentGossip() != null
                        ? getSelfParentGossip().getGeneration()
                        : EventConstants.GENERATION_UNDEFINED;
        final long otherParentGen = fakeGeneration >= GraphGenerations.FIRST_GENERATION
                ? fakeGeneration - 1
                : getOtherParentGossip() != null
                        ? getOtherParentGossip().getGeneration()
                        : EventConstants.GENERATION_UNDEFINED;

        final EventDescriptor selfParent = getSelfParentGossip() != null
                ? new EventDescriptor(
                        getSelfParentGossip().getHashedData().getHash(),
                        creatorId,
                        selfParentGen,
                        EventConstants.BIRTH_ROUND_UNDEFINED)
                : selfParentGen > EventConstants.GENERATION_UNDEFINED
                        ? new EventDescriptor(
                                RandomUtils.randomHash(random),
                                creatorId,
                                selfParentGen,
                                EventConstants.BIRTH_ROUND_UNDEFINED)
                        : null;
        final EventDescriptor otherParent = getOtherParentGossip() != null
                ? new EventDescriptor(
                        getOtherParentGossip().getHashedData().getHash(),
                        getOtherParentGossip().getHashedData().getCreatorId(),
                        otherParentGen,
                        EventConstants.BIRTH_ROUND_UNDEFINED)
                : otherParentGen > EventConstants.GENERATION_UNDEFINED
                        ? new EventDescriptor(
                                RandomUtils.randomHash(random),
                                creatorId,
                                otherParentGen,
                                EventConstants.BIRTH_ROUND_UNDEFINED)
                        : null;
        final BaseEventHashedData hashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                creatorId,
                selfParent,
                otherParent == null ? Collections.emptyList() : Collections.singletonList(otherParent),
                EventConstants.BIRTH_ROUND_UNDEFINED,
                timestamp == null ? getParentTime().plusMillis(1 + creatorId.id()) : timestamp,
                tr);

        if (fakeHash) {
            hashedData.setHash(RandomUtils.randomHash(random));
        } else {
            CryptographyHolder.get().digestSync(hashedData);
        }

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                getOtherParentGossip() == null
                        ? null
                        : getOtherParentGossip().getHashedData().getCreatorId(),
                sig);
        final GossipEvent gossipEvent = new GossipEvent(hashedData, unhashedData);
        gossipEvent.buildDescriptor();
        return gossipEvent;
    }

    public EventImpl buildEventImpl() {
        final EventImpl event = new EventImpl(buildGossipEvent(), getSelfParentImpl(), getOtherParentImpl());
        event.setConsensus(consensus);
        return event;
    }

    public GossipEventBuilder reset() {
        return setDefaults();
    }
}
