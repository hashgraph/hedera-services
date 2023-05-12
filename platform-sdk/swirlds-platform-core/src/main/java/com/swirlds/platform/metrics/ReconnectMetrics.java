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

package com.swirlds.platform.metrics;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.CommonUtils;

/**
 * Collection of metrics related to reconnects
 */
public class ReconnectMetrics {

    public static final String RECONNECT_CATEGORY = "Reconnect";

    private static final Counter.Config SENDER_START_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "startsReconnectAsSender")
            .withDescription("number of times a node starts reconnect as a sender");
    private final Counter senderStartTimes;

    private static final Counter.Config RECEIVER_START_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "startsReconnectAsReceiver")
            .withDescription("number of times a node starts reconnect as a receiver");
    private final Counter receiverStartTimes;

    private static final Counter.Config SENDER_END_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "endsReconnectAsSender")
            .withDescription("number of times a node ends reconnect as a sender");
    private final Counter senderEndTimes;

    private static final Counter.Config RECEIVER_END_TIMES_CONFIG = new Counter.Config(
                    RECONNECT_CATEGORY, "endsReconnectAsReceiver")
            .withDescription("number of times a node ends reconnect as a receiver");
    private final Counter receiverEndTimes;

    /**
     * Constructor of {@code ReconnectMetrics}
     *
     * @param metrics
     * 		reference to the metrics-system
     * @throws IllegalArgumentException if {@code metrics} is {@code null}
     */
    public ReconnectMetrics(final Metrics metrics) {
        CommonUtils.throwArgNull(metrics, "metrics");
        senderStartTimes = metrics.getOrCreate(SENDER_START_TIMES_CONFIG);
        receiverStartTimes = metrics.getOrCreate(RECEIVER_START_TIMES_CONFIG);
        senderEndTimes = metrics.getOrCreate(SENDER_END_TIMES_CONFIG);
        receiverEndTimes = metrics.getOrCreate(RECEIVER_END_TIMES_CONFIG);
    }

    public void incrementSenderStartTimes() {
        senderStartTimes.increment();
    }

    public void incrementReceiverStartTimes() {
        receiverStartTimes.increment();
    }

    public void incrementSenderEndTimes() {
        senderEndTimes.increment();
    }

    public void incrementReceiverEndTimes() {
        receiverEndTimes.increment();
    }
}
