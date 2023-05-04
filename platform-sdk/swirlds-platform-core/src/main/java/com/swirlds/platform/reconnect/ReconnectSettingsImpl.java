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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.platform.internal.SubSetting;
import java.time.Duration;

/**
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class ReconnectSettingsImpl extends SubSetting implements ReconnectSettings {

    /**
     * Determines what a node will do when it falls behind. If true, it will attempt a reconnect, if false, it will die.
     */
    public boolean active = true;

    /**
     * Defines a window of time after the node starts up when the node is allowed to reconnect. If -1 then
     * a node is always allowed to reconnect. Respects {@link #active} -- if active is false then reconnect
     * is never allowed.
     */
    public int reconnectWindowSeconds = -1;

    /**
     * The fraction of neighbors needed to tell us we have fallen behind before we initiate a reconnect.
     */
    public double fallenBehindThreshold = 0.50;

    /**
     * The amount of time that an {@link AsyncInputStream} and
     * {@link AsyncOutputStream} will wait before throwing a timeout.
     */
    public static int asyncStreamTimeoutMilliseconds = 100_000;

    /**
     * In order to ensure that data is not languishing in the asyncOutputStream buffer a periodic flush
     * is performed.
     */
    public static int asyncOutputStreamFlushMilliseconds = 100;

    public static int asyncStreamBufferSize = 10_000;

    /**
     * If false then the async streams behave as if they were synchronous. Significantly effects performance, should
     * be true unless the async streams are being debugged.
     */
    public boolean asyncStreams = true;

    /**
     * The maximum amount of time to wait for an ACK message. If no ACK is received
     * and sufficient time passes then send the potentially redundant node.
     */
    public int maxAckDelayMilliseconds = 10;

    /**
     * The maximum number of failed reconnects in a row before shutdown.
     */
    public int maximumReconnectFailuresBeforeShutdown = 10;

    /**
     * The minimum time that must pass before a node is willing to help another node
     * to reconnect another time. This prevents a node from intentionally or unintentionally slowing
     * another node down by continuously reconnecting with it. Time is measured starting from when
     * a reconnect attempt is initialized.
     */
    public Duration minimumTimeBetweenReconnects = Duration.ofMinutes(10);

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        return active;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReconnectWindowSeconds() {
        return reconnectWindowSeconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getFallenBehindThreshold() {
        return fallenBehindThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getAsyncStreamTimeoutMilliseconds() {
        return asyncStreamTimeoutMilliseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getAsyncOutputStreamFlushMilliseconds() {
        return asyncOutputStreamFlushMilliseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getAsyncStreamBufferSize() {
        return asyncStreamBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxAckDelayMilliseconds() {
        return maxAckDelayMilliseconds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaximumReconnectFailuresBeforeShutdown() {
        return maximumReconnectFailuresBeforeShutdown;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getMinimumTimeBetweenReconnects() {
        return minimumTimeBetweenReconnects;
    }
}
