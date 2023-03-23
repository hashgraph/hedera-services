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

package com.swirlds.demo.addressbook;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.ByteUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the AddressBookTestingTool.
 */
public class AddressBookTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {

    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolState.class);

    /** modify this value to change how updateStake behaves. */
    private int stakingProfile = 1;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final long CLASS_ID = 0xf052378c7364ef47L;

    private long selfId;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    /**
     * The timestamp of the first event after genesis.
     */
    private Instant genesisTimestamp;

    private boolean immutable;

    public AddressBookTestingToolState() {

        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor.
     */
    private AddressBookTestingToolState(final AddressBookTestingToolState that) {
        super(that);
        this.runningSum = that.runningSum;
        this.genesisTimestamp = that.genesisTimestamp;
        this.selfId = that.selfId;
        that.immutable = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isImmutable() {
        return immutable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized AddressBookTestingToolState copy() {
        throwIfImmutable();
        return new AddressBookTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {

        logger.info(STARTUP.getMarker(), "init called in State.");
        throwIfImmutable();

        if (trigger == InitTrigger.GENESIS) {
            parseArguments(((PlatformWithDeprecatedMethods) platform).getParameters());
        }

        this.selfId = platform.getSelfId().getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        throwIfImmutable();
        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final ConsensusEvent event = eventIterator.next();
            captureTimestamp(event);
            event.consensusTransactionIterator().forEachRemaining(this::handleTransaction);
        }
    }

    /**
     * Save the event's timestamp, if needed.
     */
    private void captureTimestamp(final ConsensusEvent event) {
        if (genesisTimestamp == null) {
            genesisTimestamp = event.getConsensusTimestamp();
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(final ConsensusTransaction transaction) {
        final int delta = ByteUtils.byteArrayToInt(transaction.getContents(), 0);
        runningSum += delta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(runningSum);
        out.writeInstant(genesisTimestamp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        runningSum = in.readLong();
        genesisTimestamp = in.readInstant();
    }

    /**
     * {@inheritDoc}
     */
    private void parseArguments(final String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Expected no arguments. See javadocs for details.");
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
        return ClassVersion.ORIGINAL;
    }

    @Override
    public AddressBook updateStake(AddressBook addressBook) {
        logger.info("updateStake called in State. Staking Profile: {}", stakingProfile);
        switch (stakingProfile) {
            case 1:
                return stakingProfile1(addressBook);
            case 2:
                return stakingProfile2(addressBook);
            default:
                logger.info(STARTUP.getMarker(), "Staking Profile {}: no change to address book.", stakingProfile);
                return addressBook;
        }
    }

    /**
     * All nodes received 10 stake.
     *
     * @param addressBook the address book to update.
     * @return the updated address book.
     */
    private AddressBook stakingProfile1(AddressBook addressBook) {
        logger.info(STARTUP.getMarker(), "Staking Profile 1: updating all nodes to have 10 stake.");
        for (int i = 0; i < addressBook.getSize(); i++) {
            addressBook.updateStake(i, 10);
        }
        return addressBook;
    }

    /**
     * All nodes received stake equal to their nodeId.
     *
     * @param addressBook the address book to update.
     * @return the updated address book.
     */
    private AddressBook stakingProfile2(AddressBook addressBook) {
        logger.info(STARTUP.getMarker(), "Staking Profile 2: updating all nodes to have stake equal to their nodeId.");
        for (int i = 0; i < addressBook.getSize(); i++) {
            addressBook.updateStake(i, i);
        }
        return addressBook;
    }
}
