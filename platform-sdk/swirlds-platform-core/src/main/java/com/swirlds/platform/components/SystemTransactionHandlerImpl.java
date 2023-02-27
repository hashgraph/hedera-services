/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.components.common.output.StateSignature;
import com.swirlds.platform.components.common.output.StateSignatureConsumer;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Handles all system transactions */
public class SystemTransactionHandlerImpl implements SystemTransactionHandler {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SystemTransactionHandlerImpl.class);

    private final StateSignatureConsumer signatureConsumer;

    /**
     * Constructor
     *
     * @param consumer
     * 		consumer of state signatures
     */
    public SystemTransactionHandlerImpl(final StateSignatureConsumer consumer) {
        this.signatureConsumer = consumer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handlePreConsensusSystemTransactions(final EventImpl event) {
        for (final Iterator<SystemTransaction> it = event.systemTransactionIterator(); it.hasNext(); ) {
            final SystemTransaction trans = it.next();
            handleSystemTransaction(trans, event.getCreatorId(), false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handlePostConsensusSystemTransactions(final ConsensusRound round) {
        for (final EventImpl event : round.getConsensusEvents()) {
            for (final Iterator<SystemTransaction> it = event.systemTransactionIterator(); it.hasNext(); ) {
                final SystemTransaction trans = it.next();
                handleSystemTransaction(trans, event.getCreatorId(), true);
            }
        }
    }

    private void handleSystemTransaction(
            final SystemTransaction transaction, final long creatorId, final boolean isConsensus) {
        try {
            switch (transaction.getType()) {
                case SYS_TRANS_STATE_SIG: // a signature on a signed state
                    final StateSignatureTransaction signatureTransaction = (StateSignatureTransaction) transaction;

                    final StateSignature signature = new StateSignature(
                            signatureTransaction.getRound(),
                            creatorId,
                            signatureTransaction.getStateHash(),
                            signatureTransaction.getStateSignature());

                    signatureConsumer.handleStateSignature(signature, isConsensus);

                    break;
                case SYS_TRANS_PING_MICROSECONDS: // latency between members
                case SYS_TRANS_BITS_PER_SECOND: // throughput between members
                    break;

                default:
                    logger.error(EXCEPTION.getMarker(), "Unknown system transaction type {}", transaction.getType());
                    break;
            }
        } catch (final RuntimeException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Error while handling system transaction: "
                            + "type: {}, id: {}, isConsensus: {}, transaction: {}, error:",
                    transaction.getType(),
                    creatorId,
                    isConsensus,
                    transaction,
                    e);
        }
    }
}
