/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.settings;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import java.time.Duration;

/**
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public interface ReconnectSettings {

    /**
     * Is reconnect enabled? If a node falls behind, if this is false the node will die, and if true the node
     * will attempt to reconnect.
     */
    boolean isActive();

    /**
     * If -1 then reconnect is always allowed (as long as {@link #isActive()} is true). If a positive integer,
     * only allow reconnects if the reconnect falls within a time window starting when the node first turns on.
     */
    int getReconnectWindowSeconds();

    /**
     * The fraction of neighbors that this node will require to report fallen behind before the node
     * will consider itself to have fallen behind.
     */
    double getFallenBehindThreshold();

    /**
     * The amount of time that an {@link AsyncInputStream} and
     * {@link AsyncOutputStream} will wait before throwing a timeout.
     */
    int getAsyncStreamTimeoutMilliseconds();

    /**
     * @return The maximum time between {@link AsyncInputStream} flushes.
     */
    int getAsyncOutputStreamFlushMilliseconds();

    /**
     * The size of the buffers for async input and output streams.
     */
    int getAsyncStreamBufferSize();

    /**
     * If no ACK is received and this many time passes then send the potentially redundant node.
     *
     * @return The maximum amount of time to wait for an ACK message.
     */
    int getMaxAckDelayMilliseconds();

    /**
     * The maximum number of allowable reconnect failures in a row before a node shuts itself down.
     */
    int getMaximumReconnectFailuresBeforeShutdown();

    /**
     * The minimum time that must pass before a node is willing to help another to reconnect.
     */
    Duration getMinimumTimeBetweenReconnects();
}
