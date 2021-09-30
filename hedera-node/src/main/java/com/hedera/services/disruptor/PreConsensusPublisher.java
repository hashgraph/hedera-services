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
import com.swirlds.common.SwirldTransaction;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PreConsensusPublisher {
    private static final Logger logger = LogManager.getLogger(PreConsensusPublisher.class);

    RingBuffer<TransactionEvent> ringBuffer;
    ExpandHandleSpan expandHandleSpan;

    @AssistedInject
    public PreConsensusPublisher(
            @Assisted RingBuffer<TransactionEvent> ringBuffer,
            ExpandHandleSpan expandHandleSpan
    ) {
        this.ringBuffer = ringBuffer;
        this.expandHandleSpan = expandHandleSpan;
    }

    /**
     * Publishes a pre-consensus transaction into the next available ring buffer slot. Note we accept the
     * same arguments that are received by ServicesState.expandSignatures and do not bother to wrap these
     * arguments in an event object. The ring buffer is composed of event objects which should just be populated.
     * This pattern cuts down on unnecessary object creation.
     */
    public void submit(SwirldTransaction transaction) {
        long sequence = ringBuffer.next();
        TransactionEvent event = ringBuffer.get(sequence);

        try
        {
            // Do the transaction lookup here where we're single-threaded. The disruptor handlers will
            // be running in parallel so there's no point in raising contention there.
            final PlatformTxnAccessor accessor = expandHandleSpan.track(transaction);
            event.setAccessor(accessor);
        } catch (InvalidProtocolBufferException e) {
            event.setErrored(true);
            logger.warn("Consensus platform txn was not gRPC!", e);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @AssistedFactory
    public interface Factory {
        PreConsensusPublisher create(RingBuffer<TransactionEvent> ringBuffer);
    }
}
