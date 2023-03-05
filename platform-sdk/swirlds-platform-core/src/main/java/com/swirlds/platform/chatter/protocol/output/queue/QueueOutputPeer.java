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

package com.swirlds.platform.chatter.protocol.output.queue;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.output.SendCheck;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Buffers messages in a queue to be sent to one particular peer
 *
 * @param <T>
 * 		the type of message sent
 */
public class QueueOutputPeer<T extends SelfSerializable> implements MessageProvider {
    private final ArrayBlockingQueue<T> queue;
    private final CommunicationState communicationState;
    private final SendCheck<T> sendCheck;

    public QueueOutputPeer(
            final int queueCapacity, final CommunicationState communicationState, final SendCheck<T> sendCheck) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.communicationState = communicationState;
        this.sendCheck = sendCheck;
    }

    /**
     * Add a message to the queue to be sent
     *
     * @param message
     * 		the message to add
     */
    public void add(final T message) {
        if (!communicationState.shouldChatter()) {
            return;
        }
        if (!queue.offer(message)) {
            communicationState.queueOverFlow();
            queue.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SelfSerializable getMessage() {
        T message;
        while ((message = queue.peek()) != null) {
            switch (sendCheck.shouldSend(message)) {
                case SEND -> {
                    queue.poll();
                    return message;
                }
                case DISCARD -> queue.poll();
                case WAIT -> {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * @return the size of the queue held by this instance
     */
    public int getQueueSize() {
        return queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
