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

import static com.swirlds.common.metrics.FloatFormats.FORMAT_8_1;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.output.MessageOutput;
import com.swirlds.platform.chatter.protocol.output.SendCheck;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MessageOutput} that has a separate queue for each peer. When a message is supposed to be sent, it adds it to
 * each individual peer's queue
 *
 * @param <T>
 * 		the type of message
 */
public class QueueOutputMain<T extends SelfSerializable> implements MessageOutput<T> {
    private final int queueCapacity;
    private final List<QueueOutputPeer<T>> peerInstances;
    private final AverageAndMax stats;

    public QueueOutputMain(final String queueName, final int queueCapacity, final Metrics metrics) {
        this.queueCapacity = queueCapacity;
        peerInstances = new ArrayList<>();
        stats = new AverageAndMax(
                metrics,
                "chatter",
                queueName + "Queue",
                "size of " + queueName + " queue",
                FORMAT_8_1,
                AverageStat.WEIGHT_VOLATILE);
    }

    /**
     * {@inheritDoc}
     */
    public void send(final T message) {
        for (final QueueOutputPeer<T> outputPeer : peerInstances) {
            outputPeer.add(message);
            stats.update(outputPeer.getQueueSize());
        }
    }

    /**
     * {@inheritDoc}
     */
    public MessageProvider createPeerInstance(
            final CommunicationState communicationState, final SendCheck<T> sendCheck) {
        final QueueOutputPeer<T> queueOutputPeer = new QueueOutputPeer<>(queueCapacity, communicationState, sendCheck);
        peerInstances.add(queueOutputPeer);
        return queueOutputPeer;
    }
}
