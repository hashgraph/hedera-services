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

package com.swirlds.demo.stats.signing;
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

import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This demo collects statistics on the running of the network and consensus systems. It writes them to the
 * screen, and also saves them to disk in a comma separated value (.csv) file. Optionally, it can also put a
 * sequence number into each transaction, and check if any are lost, or delayed too long. Each transaction
 * is 100 random bytes. So StatsSigningDemoState.handleTransaction doesn't actually do anything, other than the
 * optional sequence number check.
 */
@ConstructableIgnored
public class StatsSigningTestingToolState extends PlatformMerkleStateRoot {

    private static final long CLASS_ID = 0x79900efa3127b6eL;
    /**
     * use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(StatsSigningTestingToolState.class);

    private final Supplier<SttTransactionPool> transactionPoolSupplier;

    /** A running sum of transaction contents */
    private long runningSum = 0;

    /** if true, artificially take {@link #HANDLE_MICROS} to handle each consensus transaction */
    private static final boolean SYNTHETIC_HANDLE_TIME = false;

    /** the number of microseconds to wait before returning from the handle method */
    private static final int HANDLE_MICROS = 100;

    public StatsSigningTestingToolState(
            @NonNull final MerkleStateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory,
            @NonNull final Supplier<SttTransactionPool> transactionPoolSupplier) {
        super(lifecycles, versionFactory);
        this.transactionPoolSupplier = Objects.requireNonNull(transactionPoolSupplier);
    }

    private StatsSigningTestingToolState(@NonNull final StatsSigningTestingToolState sourceState) {
        super(sourceState);
        this.transactionPoolSupplier = sourceState.transactionPoolSupplier;
        setImmutable(false);
        sourceState.setImmutable(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized StatsSigningTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new StatsSigningTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(
            @NonNull final Event event,
            @NonNull
                    final Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>>
                            stateSignatureTransactions) {
        final SttTransactionPool sttTransactionPool = transactionPoolSupplier.get();
        if (sttTransactionPool != null) {
            event.forEachTransaction(transaction -> {
                if (transaction.isSystem()) {
                    return;
                }
                final TransactionSignature transactionSignature =
                        sttTransactionPool.expandSignatures(transaction.getApplicationTransaction());
                if (transactionSignature != null) {
                    transaction.setMetadata(transactionSignature);
                    CryptographyHolder.get().verifySync(List.of(transactionSignature));
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(
            @NonNull final Round round,
            @NonNull final PlatformStateModifier platformState,
            @NonNull
                    final Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>>
                            stateSignatureTransactions) {
        throwIfImmutable();
        round.forEachTransaction(this::handleTransaction);
    }

    private void handleTransaction(final ConsensusTransaction trans) {
        if (trans.isSystem()) {
            return;
        }
        final TransactionSignature s = trans.getMetadata();

        if (s != null && validateSignature(s, trans) && s.getSignatureStatus() != VerificationStatus.VALID) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Invalid Transaction Signature [ transactionId = {}, status = {}, signatureType = {},"
                            + " publicKey = {}, signature = {}, data = {} ]",
                    TransactionCodec.txId(trans.getApplicationTransaction()),
                    s.getSignatureStatus(),
                    s.getSignatureType(),
                    hex(Arrays.copyOfRange(
                            s.getContentsDirect(),
                            s.getPublicKeyOffset(),
                            s.getPublicKeyOffset() + s.getPublicKeyLength())),
                    hex(Arrays.copyOfRange(
                            s.getContentsDirect(),
                            s.getSignatureOffset(),
                            s.getSignatureOffset() + s.getSignatureLength())),
                    hex(Arrays.copyOfRange(
                            s.getContentsDirect(), s.getMessageOffset(), s.getMessageOffset() + s.getMessageLength())));
        }

        runningSum += TransactionCodec.txId(trans.getApplicationTransaction());

        maybeDelay();
    }

    private void maybeDelay() {
        if (SYNTHETIC_HANDLE_TIME) {
            final long start = System.nanoTime();
            while (System.nanoTime() - start < (HANDLE_MICROS * 1_000)) {
                // busy wait
            }
        }
    }

    private boolean validateSignature(final TransactionSignature signature, final Transaction transaction) {
        try {
            final Future<Void> future = signature.waitForFuture();
            // Block & Ignore the Void return
            future.get();
            return true;
        } catch (final InterruptedException e) {
            logger.info(
                    TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
                    "handleTransaction Interrupted. This should happen only during a reconnect");
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error while validating transaction signature for transaction {}",
                    transaction,
                    e);
        }
        return false;
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
        return ClassVersion.MIGRATE_TO_SERIALIZABLE;
    }

    /**
     * The version history of this class.
     * Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        /**
         * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
         * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
         * specially by the platform.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, serialization was performed by serialize/deserialize.
         */
        public static final int MIGRATE_TO_SERIALIZABLE = 2;

        /**
         * In this version, a transaction counter was added to the state.
         */
        public static final int KEEP_STATE = 3;

        public static final int NO_ADDRESS_BOOK_IN_STATE = 4;
    }
}
