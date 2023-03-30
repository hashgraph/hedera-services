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

import com.swirlds.common.config.singleton.ConfigurationHolder;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the AddressBookTestingTool.
 */
public class AddressBookTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {

    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolState.class);

    /** modify this value to change how updateStake behaves. */
    private int stakingBehavior = 0;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final long CLASS_ID = 0xf052378c7364ef47L;

    private long selfId;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    public AddressBookTestingToolState() {
        logger.info(STARTUP.getMarker(), "New State Constructed.");
        stakingBehavior = ConfigurationHolder.getConfigData(AddressBookTestingToolConfig.class)
                .stakingBehavior();
    }

    /**
     * Copy constructor.
     */
    private AddressBookTestingToolState(@NonNull final AddressBookTestingToolState that) {
        super(that);
        Objects.requireNonNull(that, "the address book testing tool state to copy cannot be null");
        this.runningSum = that.runningSum;
        this.selfId = that.selfId;
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
            @NonNull final Platform platform,
            @NonNull final SwirldDualState swirldDualState,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        Objects.requireNonNull(platform, "the platform cannot be null");
        Objects.requireNonNull(swirldDualState, "the swirld dual state cannot be null");
        Objects.requireNonNull(trigger, "the init trigger cannot be null");

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
    public void handleConsensusRound(@NonNull final Round round, @NonNull final SwirldDualState swirldDualState) {
        Objects.requireNonNull(round, "the round cannot be null");
        Objects.requireNonNull(swirldDualState, "the swirld dual state cannot be null");
        throwIfImmutable();

        final Iterator<ConsensusEvent> eventIterator = round.iterator();

        while (eventIterator.hasNext()) {
            final ConsensusEvent event = eventIterator.next();
            event.consensusTransactionIterator().forEachRemaining(this::handleTransaction);
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(@NonNull final ConsensusTransaction transaction) {
        final int delta = ByteUtils.byteArrayToInt(transaction.getContents(), 0);
        runningSum += delta;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(out, "the serializable data output stream cannot be null");
        out.writeLong(runningSum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        Objects.requireNonNull(in, "the serializable data input stream cannot be null");
        runningSum = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    private void parseArguments(@NonNull final String[] args) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AddressBook updateStake(@NonNull final AddressBook addressBook) {
        Objects.requireNonNull(addressBook, "the address book cannot be null");
        logger.info("updateStake called in State. Staking Behavior: {}", stakingBehavior);
        switch (stakingBehavior) {
            case 1:
                return stakingBehavior1(addressBook);
            case 2:
                return stakingBehavior2(addressBook);
            default:
                logger.info(STARTUP.getMarker(), "Staking Behavior {}: no change to address book.", stakingBehavior);
                return addressBook;
        }
    }

    /**
     * All nodes received 10 stake.
     *
     * @param addressBook the address book to update.
     * @return the updated address book.
     */
    @NonNull
    private AddressBook stakingBehavior1(@NonNull final AddressBook addressBook) {
        logger.info(STARTUP.getMarker(), "Staking Behavior 1: updating all nodes to have 10 stake.");
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
    @NonNull
    private AddressBook stakingBehavior2(@NonNull final AddressBook addressBook) {
        logger.info(STARTUP.getMarker(), "Staking Behavior 2: updating all nodes to have stake equal to their nodeId.");
        for (int i = 0; i < addressBook.getSize(); i++) {
            addressBook.updateStake(i, i);
        }
        return addressBook;
    }
}
