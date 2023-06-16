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

import com.swirlds.common.config.StateConfig;
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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * <p>
     * A deep copy of this object is NOT created when this state is copied
     */
    private final TransactionHandlingHistory transactionHandlingHistory;

    /**
     * The true "state" of this app. This long value is updated with every transaction, and with every round
     */
    private long stateLong = 0;

    /**
     * Constructor
     */
    public ConsistencyTestingToolState() {
        logger.info(STARTUP.getMarker(), "New State Constructed.");

        this.transactionHandlingHistory = new TransactionHandlingHistory();
    }

    /**
     * Copy constructor
     *
     * @param that the state to copy
     */
    private ConsistencyTestingToolState(@NonNull final ConsistencyTestingToolState that) {
        super(Objects.requireNonNull(that));

        this.transactionHandlingHistory = that.transactionHandlingHistory;
        this.stateLong = that.stateLong;
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

        Objects.requireNonNull(platform);
        Objects.requireNonNull(swirldDualState);
        Objects.requireNonNull(trigger);

        final StateConfig stateConfig = platform.getContext().getConfiguration().getConfigData(StateConfig.class);
        final ConsistencyTestingToolConfig testingToolConfig =
                platform.getContext().getConfiguration().getConfigData(ConsistencyTestingToolConfig.class);

        final Path logFileDirectory = stateConfig
                .savedStateDirectory()
                .resolve(testingToolConfig.logfileDirectory())
                .resolve(Long.toString(platform.getSelfId().id()));
        try {
            Files.createDirectories(logFileDirectory);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to set up file system for consistency data", e);
        }
        final Path logFilePath = logFileDirectory.resolve("ConsistencyTestLog.csv");

        transactionHandlingHistory.init(logFilePath);
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
        Objects.requireNonNull(out);
        out.writeLong(stateLong);
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
    @NonNull
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
        Objects.requireNonNull(round);
        Objects.requireNonNull(swirldDualState);

        round.forEachTransaction(this::applyTransactionToState);
        stateLong = NonCryptographicHashing.hash64(stateLong, round.getRoundNum());

        transactionHandlingHistory.processRound(ConsistencyTestingToolRound.fromRound(round, stateLong));
    }
}
