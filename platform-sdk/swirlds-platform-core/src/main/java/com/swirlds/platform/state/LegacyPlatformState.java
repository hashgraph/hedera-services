/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * State written and used by the platform.
 *
 * @deprecated this class is being replaced
 */
@Deprecated(forRemoval = true)
public class LegacyPlatformState extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0x5bcd37c8b3dd97f5L;

    private static final class ClassVersion {
        /** start using mingen when loading consensus */
        public static final int LOAD_MINGEN_INTO_CONSENSUS = 4;

        public static final int ADDED_SOFTWARE_VERSION = 5;
    }

    /**
     * Round number of the last round for which all the famous witnesses are known. The signed state is a
     * function of all consensus events at the time this is created, but some of the events in this round
     * and earlier rounds may not yet be consensus events.
     */
    private long round;

    /**
     * how many consensus events have there been throughout all of history, up through the round received
     * that this SignedState represents.
     */
    private long numEventsCons;
    /**
     * running hash of the hashes of all consensus events have there been throughout all of history, up
     * through the round received that this SignedState represents.
     */
    private Hash hashEventsCons;
    /** the address book at the moment of signing */
    private AddressBook addressBook;
    /** contains events for the round that is being signed and the preceding rounds */
    private EventImpl[] events;
    /** the consensus timestamp for this signed state */
    private Instant consensusTimestamp;
    /** the minimum generation of famous witnesses per round */
    private List<MinGenInfo> minGenInfo;
    /** the timestamp of the last transactions handled by this state */
    private Instant lastTransactionTimestamp;

    /**
     * The version of the application software that was responsible for creating this state.
     */
    private SoftwareVersion creationSoftwareVersion;

    public LegacyPlatformState() {
        this.events = new EventImpl[0];
    }

    private LegacyPlatformState(final LegacyPlatformState that) {
        super(that);
        this.round = that.round;
        this.numEventsCons = that.numEventsCons;
        this.hashEventsCons = that.hashEventsCons;
        this.addressBook = that.addressBook;
        if (that.events != null) {
            this.events = Arrays.copyOf(that.events, that.events.length);
        }
        this.consensusTimestamp = that.consensusTimestamp;
        if (that.minGenInfo != null) {
            this.minGenInfo = new ArrayList<>(that.minGenInfo);
        }
        this.lastTransactionTimestamp = that.lastTransactionTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode migrate(final int version) {
        final PlatformState state = new PlatformState();

        final PlatformData platformData = new PlatformData()
                .setRound(round)
                .setNumEventsCons(numEventsCons)
                .setHashEventsCons(hashEventsCons)
                .setEvents(events)
                .setMinGenInfo(minGenInfo)
                .setConsensusTimestamp(consensusTimestamp)
                .setCreationSoftwareVersion(creationSoftwareVersion)
                .setLastTransactionTimestamp(lastTransactionTimestamp);

        state.setPlatformData(platformData);
        state.setAddressBook(addressBook);

        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException("deprecated");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        round = in.readLong();
        numEventsCons = in.readLong();

        hashEventsCons = in.readSerializable(false, Hash::new);

        addressBook = in.readSerializable(false, AddressBook::new);

        int eventNum = in.readInt();
        events = new EventImpl[eventNum];
        for (int i = 0; i < eventNum; i++) {
            events[i] = in.readSerializable(false, EventImpl::new);
            events[i].getBaseEventHashedData().setHash(in.readSerializable(false, Hash::new));
            events[i].markAsSignedStateEvent();
        }

        consensusTimestamp = in.readInstant();
        minGenInfo = Utilities.readList(in, LinkedList::new, stream -> {
            long round = stream.readLong();
            long minimumGeneration = stream.readLong();
            return new MinGenInfo(round, minimumGeneration);
        });

        State.linkParents(events);

        lastTransactionTimestamp = in.readInstant();

        if (version >= ClassVersion.ADDED_SOFTWARE_VERSION) {
            creationSoftwareVersion = in.readSerializable();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ADDED_SOFTWARE_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.LOAD_MINGEN_INTO_CONSENSUS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LegacyPlatformState copy() {
        throw new UnsupportedOperationException("deprecated");
    }

    public void setCreationSoftwareVersion(final SoftwareVersion creationVersion) {
        this.creationSoftwareVersion = creationVersion;
    }

    public void setRound(long round) {
        this.round = round;
    }

    public void setNumEventsCons(long numEventsCons) {
        this.numEventsCons = numEventsCons;
    }

    public void setHashEventsCons(Hash hashEventsCons) {
        this.hashEventsCons = hashEventsCons;
    }

    public void setAddressBook(AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    public void setEvents(EventImpl[] events) {
        this.events = events;
    }

    public void setConsensusTimestamp(Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }

    public void setMinGenInfo(List<MinGenInfo> minGenInfo) {
        this.minGenInfo = minGenInfo;
    }

    public Instant getLastTransactionTimestamp() {
        return lastTransactionTimestamp;
    }

    public void setLastTransactionTimestamp(Instant lastTransactionTimestamp) {
        this.lastTransactionTimestamp = lastTransactionTimestamp;
    }
}
