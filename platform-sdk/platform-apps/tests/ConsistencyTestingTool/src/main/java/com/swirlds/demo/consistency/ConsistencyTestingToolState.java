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

package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.utility.NonCryptographicHashing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the Consistency Testing Tool
 */
public class ConsistencyTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {
    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolState.class);
    private static final long CLASS_ID = 0xda03bb07eb897d82L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The history of transactions that have been handled by this app
     */
    private final TransactionHandlingHistory transactionHandlingHistory;

    /**
     * The true "state" of this app. This long value is updated with every transaction
     */
    private long stateLong = 0;

    /**
     * Constructor
     *
     * @param permitRoundGaps whether or not gaps in the round history will be permitted in the test. if false, an error
     *                        will be logged if a gap is found
     */
    public ConsistencyTestingToolState(final boolean permitRoundGaps) {
        transactionHandlingHistory = new TransactionHandlingHistory(permitRoundGaps);

        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Copy constructor
     *
     * @param that the state to copy
     */
    private ConsistencyTestingToolState(@NonNull final ConsistencyTestingToolState that) {
        super(Objects.requireNonNull(that));

        this.transactionHandlingHistory = new TransactionHandlingHistory(that.transactionHandlingHistory);
        this.stateLong = that.stateLong;
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

        transactionHandlingHistory.tryParseLog();
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
    public void serialize(final @NonNull SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(out).writeLong(stateLong);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final @NonNull SerializableDataInputStream in, final int version) throws IOException {
        stateLong = Objects.requireNonNull(in).readLong();
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
    public synchronized ConsistencyTestingToolState copy() {
        throwIfImmutable();
        return new ConsistencyTestingToolState(this);
    }

    /**
     * Sets the new {@link #stateLong} to the non-cryptographic hash of the existing state, and the contents of the
     * transaction being handled
     *
     * @param transaction the transaction to apply to the state
     */
    private void applyTransactionToState(final @NonNull ConsensusTransaction transaction) {
        final long transactionContents =
                byteArrayToLong(Objects.requireNonNull(transaction).getContents(), 0);

        stateLong = NonCryptographicHashing.hash64(stateLong, transactionContents);
    }

    /**
     * Modifies the state based on each transaction in the round
     * <p>
     * Writes the round and its contents to a log on disk
     */
    @Override
    public void handleConsensusRound(final @NonNull Round round, final @NonNull SwirldDualState swirldDualState) {
        round.forEachTransaction(this::applyTransactionToState);

        transactionHandlingHistory.processRound(ConsistencyTestingToolRound.fromRound(round));
    }
}
