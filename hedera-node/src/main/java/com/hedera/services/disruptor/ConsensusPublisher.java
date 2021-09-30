package com.hedera.services.disruptor;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.lmax.disruptor.RingBuffer;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldTransaction;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

public class ConsensusPublisher {
    private static final Logger logger = LogManager.getLogger(ConsensusPublisher.class);

    RingBuffer<TransactionEvent> ringBuffer;
    ExpandHandleSpan expandHandleSpan;

    @AssistedInject
    public ConsensusPublisher(
            @Assisted RingBuffer<TransactionEvent> ringBuffer,
            ExpandHandleSpan expandHandleSpan
    ) {
        this.ringBuffer = ringBuffer;
        this.expandHandleSpan = expandHandleSpan;
    }

    /**
     * Publishes a consensus transaction into the next available ring buffer slot. Note we accept the
     * same arguments that are received by ServicesState.handleTransaction and do not bother to wrap these
     * arguments in an event object. The ring buffer is composed of event objects which should just be populated.
     * This pattern cuts down on unnecessary object creation.
     *
     * @param submittingMember the ID number of the member who created this transaction
     * @param creationTime the time when this transaction was first created and sent to the network, as claimed by
     *                     the member that created it (which might be dishonest or mistaken)
     * @param consensusTime the consensus timestamp for when this transaction happened (or an estimate of it, if it
     *                      hasn't reached consensus yet)
     * @param transaction the transaction to handle, encoded any way the swirld app author chooses
     * @param dualState current dualState object which can be read/written by the application
     */
    public void submit(
            long submittingMember,
            Instant creationTime,
            Instant consensusTime,
            SwirldTransaction transaction,
            SwirldDualState dualState
    ) {
        long sequence = ringBuffer.next();
        TransactionEvent event = ringBuffer.get(sequence);

        try
        {
            // Do the transaction lookup here where we're single-threaded. The disruptor handlers will
            // be running in parallel so there's no point in raising contention there.
            final PlatformTxnAccessor accessor = expandHandleSpan.accessorFor(transaction);

            event.setSubmittingMember(submittingMember);
            event.setConsensus(true);
            event.setCreationTime(creationTime);
            event.setConsensusTime(consensusTime);
            event.setAccessor(accessor);
            event.setDualState(dualState);
        } catch (InvalidProtocolBufferException e) {
            event.setErrored(true);
            logger.warn("Consensus platform txn was not gRPC!", e);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @AssistedFactory
    public interface Factory {
        ConsensusPublisher create(RingBuffer<TransactionEvent> ringBuffer);
    }
}
