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
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
public class StatsSigningTestingToolState extends PartialMerkleLeaf implements SwirldState2, MerkleLeaf {

    private static final long CLASS_ID = 0x79900efa3127b6eL;
    /**
     * use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(StatsSigningTestingToolState.class);

    private final Supplier<TransactionPool> transactionPoolSupplier;

    /** A running sum of transaction contents */
    private long runningSum = 0;

    private final long selfId;

    /** if true, artificially take {@link #HANDLE_MICROS} to handle each consensus transaction */
    private static final boolean SYNTHETIC_HANDLE_TIME = false;

    /** the number of microseconds to wait before returning from the handle method */
    private static final int HANDLE_MICROS = 100;

    public StatsSigningTestingToolState() {
        this(0L, () -> null);
    }

    public StatsSigningTestingToolState(final long selfId, final Supplier<TransactionPool> transactionPoolSupplier) {
        this.selfId = selfId;
        this.transactionPoolSupplier = transactionPoolSupplier;
    }

    private StatsSigningTestingToolState(final long selfId, final StatsSigningTestingToolState sourceState) {
        super(sourceState);
        this.selfId = selfId;
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
        return new StatsSigningTestingToolState(selfId, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(final Event event) {
        final TransactionPool transactionPool = transactionPoolSupplier.get();
        if (transactionPool != null) {
            event.forEachTransaction(transaction -> {
                transactionPool.expandSignatures(transaction);
                CryptographyHolder.get().verifyAsync(transaction.getSignatures());
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        throwIfImmutable();
        round.forEachTransaction(this::handleTransaction);
    }

    private void handleTransaction(final ConsensusTransaction trans) {
        for (final TransactionSignature s : trans.getSignatures()) {

            if (!validateSignature(s, trans)) {
                continue;
            }

            if (s.getSignatureStatus() != VerificationStatus.VALID) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Invalid Transaction Signature [ transactionId = {}, status = {}, signatureType = {},"
                                + " publicKey = {}, signature = {}, data = {} ]",
                        TransactionCodec.txId(trans.getContents()),
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
                                s.getContentsDirect(),
                                s.getMessageOffset(),
                                s.getMessageOffset() + s.getMessageLength())));
            }
        }

        runningSum += TransactionCodec.txId(trans.getContents());

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
                    "handleTransaction Interrupted [ nodeId = {} ]. This should happen only during a reconnect",
                    selfId);
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        if (getVersion() >= ClassVersion.KEEP_STATE) {
            out.writeLong(runningSum);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        if (version < ClassVersion.KEEP_STATE) {
            // In this version we serialized an address book
            in.readSerializable();
        }

        if (getVersion() >= ClassVersion.KEEP_STATE) {
            runningSum = in.readLong();
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
