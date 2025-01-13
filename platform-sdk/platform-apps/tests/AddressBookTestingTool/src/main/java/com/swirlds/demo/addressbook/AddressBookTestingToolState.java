/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.address.AddressBookUtils;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.merkle.singleton.StringLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the AddressBookTestingTool.
 */
@ConstructableIgnored
public class AddressBookTestingToolState extends PlatformMerkleStateRoot {

    private static final Logger logger = LogManager.getLogger(AddressBookTestingToolState.class);

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private static final long CLASS_ID = 0xf052378c7364ef47L;
    // 0 is PLATFORM_STATE, 1 is ROSTERS, 2 is ROSTER_STATE
    private static final int RUNNING_SUM_INDEX = 3;
    private static final int ROUND_HANDLED_INDEX = 4;

    /**
     * The true "state" of this app. Each transaction is just an integer that gets added to this value.
     */
    private long runningSum = 0;

    /**
     * The number of rounds handled by this app. Is incremented each time
     * {@link AddressBookTestingToolStateLifecycles#onHandleConsensusRound(Round, AddressBookTestingToolState)} is called. Note that this may not actually equal the round
     * number, since we don't call {@link AddressBookTestingToolStateLifecycles#onHandleConsensusRound(Round, AddressBookTestingToolState)} for rounds with no events.
     *
     * <p>
     * Affects the hash of this node.
     */
    private long roundsHandled = 0;

    public AddressBookTestingToolState(@NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(versionFactory);
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor.
     */
    private AddressBookTestingToolState(@NonNull final AddressBookTestingToolState that) {
        super(that);
        Objects.requireNonNull(that, "the address book testing tool state to copy cannot be null");
        this.runningSum = that.runningSum;
        this.roundsHandled = that.roundsHandled;
    }

    /**
     * Initializes state fields from the merkle tree children
     */
    void initState() {
        final StringLeaf runningSumLeaf = getChild(RUNNING_SUM_INDEX);
        if (runningSumLeaf != null && runningSumLeaf.getLabel() != null) {
            this.runningSum = Long.parseLong(runningSumLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with state long {}.", runningSum);
        }
        final StringLeaf roundsHandledLeaf = getChild(ROUND_HANDLED_INDEX);
        if (roundsHandledLeaf != null && roundsHandledLeaf.getLabel() != null) {
            this.roundsHandled = Long.parseLong(roundsHandledLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with {} rounds handled.", roundsHandled);
        }
    }

    void incrementRoundsHandled() {
        roundsHandled++;
        setChild(ROUND_HANDLED_INDEX, new StringLeaf(Long.toString(roundsHandled)));
    }

    void incrementRunningSum(final long value) {
        runningSum += value;
        setChild(RUNNING_SUM_INDEX, new StringLeaf(Long.toString(runningSum)));
    }

    long getRoundsHandled() {
        return roundsHandled;
    }

    @Override
    public void preHandle(
            @NonNull final Event event,
            @NonNull final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        event.transactionIterator().forEachRemaining(transaction -> {
            // We are not interested in pre-handling any system transactions, as they are
            // specific for the platform only.We also don't want to consume deprecated
            // EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in the
            // callback,since it's intended to be used only for the new form of encoded system
            // transactions in Bytes. Thus, we can directly skip the current
            // iteration, if it processes a deprecated system transaction with the
            // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
            if (transaction.isSystem()) {
                return;
            }

            // We should consume in the callback the new form of system transactions in Bytes
            if (areTransactionBytesSystemOnes(transaction)) {
                consumeSystemTransaction(transaction, event, stateSignatureTransaction);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized AddressBookTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new AddressBookTestingToolState(this);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(
            @NonNull final Round round,
            @NonNull final PlatformStateModifier platformState,
            @NonNull final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        Objects.requireNonNull(round, "the round cannot be null");
        Objects.requireNonNull(platformState, "the platform state cannot be null");
        throwIfImmutable();

        if (roundsHandled == 0 && !freezeAfterGenesis.equals(Duration.ZERO)) {
            // This is the first round after genesis.
            logger.info(
                    STARTUP.getMarker(),
                    "Setting freeze time to {} seconds after genesis.",
                    freezeAfterGenesis.getSeconds());
            platformState.setFreezeTime(round.getConsensusTimestamp().plus(freezeAfterGenesis));
        }

        roundsHandled++;
        setChild(ROUND_HANDLED_INDEX, new StringLeaf(Long.toString(roundsHandled)));

        for (final var event : round) {
            event.consensusTransactionIterator().forEachRemaining(transaction -> {
                // We are not interested in handling any system transactions, as they are
                // specific for the platform only.We also don't want to consume deprecated
                // EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in the
                // callback, since it's intended to be used only for the new form of encoded system
                // transactions in Bytes. Thus, we can directly skip the current
                // iteration, if it processes a deprecated system transaction with the
                // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
                if (transaction.isSystem()) {
                    return;
                }

                // We should consume in the callback the new form of system transactions in Bytes
                if (areTransactionBytesSystemOnes(transaction)) {
                    consumeSystemTransaction(transaction, event, stateSignatureTransaction);
                } else {
                    handleTransaction(transaction);
                }
            });
        }

        if (!validationPerformed.getAndSet(true)) {
            String testScenario = testingToolConfig.testScenario();
            if (validateTestScenario()) {
                logger.info(STARTUP.getMarker(), "Test scenario {}: finished without errors.", testScenario);
            } else {
                logger.error(EXCEPTION.getMarker(), "Test scenario {}: validation failed with errors.", testScenario);
            }
        }
    }

    /**
     * Checks if the transaction bytes are system ones. The test creates application transactions with max length of 4.
     * System transactions will be always bigger than that.
     *
     * @param transaction the consensus transaction to check
     * @return true if the transaction bytes are system ones, false otherwise
     */
    private boolean areTransactionBytesSystemOnes(final Transaction transaction) {
        return transaction.getApplicationTransaction().length() > 4;
    }

    /**
     * Converts a transaction to a {@link StateSignatureTransaction} and then consumes it into a callback.
     *
     * @param transaction the transaction to consume
     * @param event the event that contains the transaction
     * @param stateSignatureTransactionCallback the callback to call with the system transaction
     */
    private void consumeSystemTransaction(
            final Transaction transaction,
            final Event event,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        try {
            final var stateSignatureTransaction =
                    StateSignatureTransaction.PROTOBUF.parse(transaction.getApplicationTransaction());
            stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                    event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction));
        } catch (final com.hedera.pbj.runtime.ParseException e) {
            logger.error("Failed to parse StateSignatureTransaction", e);
        }
    }

    /**
     * Apply a transaction to the state.
     *
     * @param transaction the transaction to apply
     */
    private void handleTransaction(@NonNull final ConsensusTransaction transaction) {
        final int delta =
                ByteUtils.byteArrayToInt(transaction.getApplicationTransaction().toByteArray(), 0);
        runningSum += delta;
        setChild(RUNNING_SUM_INDEX, new StringLeaf(Long.toString(runningSum)));
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
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
    }
}
