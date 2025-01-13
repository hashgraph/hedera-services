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

package com.swirlds.demo.stress;
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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This testing tool simulates configurable processing times for both preHandling and handling for stress testing
 * purposes.
 */
@ConstructableIgnored
public class StressTestingToolState extends PlatformMerkleStateRoot {
    private static final long CLASS_ID = 0x79900efa3127b6eL;

    // The length of a system transaction:
    // 1 byte - encoded field position +
    // (varying length up to 10 bytes) round number long +
    // 1 byte - encoded field position +
    // 2 bytes - encoded size +
    // 384 bytes - signature
    // 1 byte - encoded field position +
    // 1 byte - encoded size +
    // 48 bytes - hash
    private static final int MIN_SYSTEM_TRANSACTION_LENGTH = 437;
    private static final int MAX_SYSTEM_TRANSACTION_LENGTH = 448;

    /** A running sum of transaction contents */
    private long runningSum = 0;

    /** supplies the app config */
    public StressTestingToolConfig config;

    public StressTestingToolState(
            @NonNull final StateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(lifecycles, versionFactory);
    }

    private StressTestingToolState(@NonNull final StressTestingToolState sourceState) {
        super(sourceState);
        runningSum = sourceState.runningSum;
        config = sourceState.config;
        setImmutable(false);
        sourceState.setImmutable(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized StressTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new StressTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        super.init(platform, trigger, previousSoftwareVersion);

        this.config = platform.getContext().getConfiguration().getConfigData(StressTestingToolConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(
            @NonNull final Event event,
            @NonNull
                    final Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                            stateSignatureTransactionCallback) {
        event.forEachTransaction(transaction -> {
            // We are not interested in pre-handling any system transactions, as they are
            // specific for the platform only.We also don't want to consume deprecated
            // EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in the
            // callback,since it's intended to be used only for the new form of encoded system
            // transactions in Bytes.Thus, we can directly skip the current
            // iteration, if it processes a deprecated system transaction with the
            // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
            if (transaction.isSystem()) {
                return;
            }

            final var stateSignatureTransaction = getStateSignatureTransaction(transaction);
            stateSignatureTransaction.ifPresent(
                    signatureTransaction -> stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                            event.getCreatorId(), event.getSoftwareVersion(), signatureTransaction)));
        });

        busyWait(config.preHandleTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(
            @NonNull final Round round,
            @NonNull final PlatformStateModifier platformState,
            @NonNull
                    final Consumer<ScopedSystemTransaction<StateSignatureTransaction>>
                            stateSignatureTransactionCallback) {
        throwIfImmutable();

        for (final var event : round) {
            event.consensusTransactionIterator().forEachRemaining(transaction -> {
                // We are not interested in handling any system transactions, as they are
                // specific for the platform only.We also don't want to consume deprecated
                // EventTransaction.STATE_SIGNATURE_TRANSACTION system transactions in the
                // callback,since it's intended to be used only for the new form of encoded system
                // transactions in Bytes.Thus, we can directly skip the current
                // iteration, if it processes a deprecated system transaction with the
                // EventTransaction.STATE_SIGNATURE_TRANSACTION type.
                if (transaction.isSystem()) {
                    return;
                }

                final var stateSignatureTransaction = getStateSignatureTransaction(transaction);

                if (stateSignatureTransaction.isPresent()) {
                    stateSignatureTransactionCallback.accept(new ScopedSystemTransaction<>(
                            event.getCreatorId(), event.getSoftwareVersion(), stateSignatureTransaction.get()));
                } else {
                    handleTransaction(transaction);
                }
            });
        }
    }

    private void handleTransaction(@NonNull final ConsensusTransaction trans) {
        runningSum +=
                ByteUtils.byteArrayToLong(trans.getApplicationTransaction().toByteArray(), 0);
        busyWait(config.handleTime());
    }

    /**
     * Tries to parse the transaction bytes to a {@link StateSignatureTransaction}.
     *
     * @param transaction the transaction to parse
     * @return optional of the {@link StateSignatureTransaction} containing the parsed system transaction
     */
    @NonNull
    private Optional<StateSignatureTransaction> getStateSignatureTransaction(@NonNull final Transaction transaction) {
        final var transactionBytes = transaction.getApplicationTransaction();

        // If the transaction does not have marker byte for an application transaction, then it should be a system one
        if (transactionBytes.getByte(0) != 1) {
            return tryToParseSystemTransaction(transactionBytes.toReadableSequentialData());
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    private Optional<StateSignatureTransaction> tryToParseSystemTransaction(
            @NonNull final ReadableSequentialData transactionBytes) {
        try {
            return Optional.of(StateSignatureTransaction.PROTOBUF.parseStrict(transactionBytes));
        } catch (final ParseException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("all")
    private void busyWait(@NonNull final Duration duration) {
        if (!duration.isZero() && !duration.isNegative()) {
            final long start = System.nanoTime();
            final long nanos = duration.toNanos();
            while (System.nanoTime() - start < nanos) {
                // busy wait
            }
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
        return ClassVersion.NO_ADDRESS_BOOK_IN_STATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.NO_ADDRESS_BOOK_IN_STATE;
    }

    /**
     * The version history of this class. Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        public static final int NO_ADDRESS_BOOK_IN_STATE = 4;
    }
}
