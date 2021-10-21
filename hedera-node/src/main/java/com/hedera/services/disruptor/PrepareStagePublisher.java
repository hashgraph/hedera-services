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

import com.hedera.services.ServicesState;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.lmax.disruptor.RingBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrepareStagePublisher {
    RingBuffer<TransactionEvent> ringBuffer;

    public PrepareStagePublisher(RingBuffer<TransactionEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * Publishes a pre-consensus transaction into the next available ring buffer slot. The ring buffer
     * is composed of pre-allocated event objects which should just be reused. This pattern cuts down
     * on unnecessary object creation.
     *
     * @param accessor the wrapped transaction to handle
     */
    public void submit(PlatformTxnAccessor accessor) {
        long sequence = ringBuffer.next();
        TransactionEvent event = ringBuffer.get(sequence);

        event.setAccessor(accessor);
        ringBuffer.publish(sequence);
    }
}
