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

package com.swirlds.common.merkle.synchronization.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for the reconnect.
 *
 * @param active
 * 		Determines what a node will do when it falls behind. If true, it will attempt a reconnect, if false, it will
 * 		die. Is reconnect enabled?
 * @param reconnectWindowSeconds
 * 		Defines a window of time after the node starts up when the node is allowed to reconnect. If -1 then a node is
 * 		always allowed to reconnect. Respects {@link #active} -- if active is false then reconnect is never allowed.
 * @param fallenBehindThreshold
 * 		The fraction of neighbors needed to tell us we have fallen behind before we initiate a reconnect.
 * @param asyncStreamTimeout
 * 		The amount of time that an {@link com.swirlds.common.merkle.synchronization.streams.AsyncInputStream} and
 *        {@link com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream} will wait before throwing a
 * 		timeout.
 * @param asyncOutputStreamFlush
 * 		In order to ensure that data is not languishing in the asyncOutputStream buffer a periodic flush is performed.
 * @param asyncStreamBufferSize
 * 		The size of the buffers for async input and output streams.
 * @param maxAckDelay
 * 		The maximum amount of time to wait for an ACK message. If no ACK is received and sufficient time passes then
 * 		send the potentially redundant node.
 * @param maximumReconnectFailuresBeforeShutdown
 * 		The maximum number of failed reconnects in a row before shutdown.
 * @param minimumTimeBetweenReconnects
 * 		The minimum time that must pass before a node is willing to help another node to reconnect another time. This
 * 		prevents a node from intentionally or unintentionally slowing another node down by continuously reconnecting
 * 		with it. Time is measured starting from when a reconnect attempt is initialized.
 */
@ConfigData("reconnect")
public record ReconnectConfig(
        @ConfigProperty(defaultValue = "true") boolean active,
        @ConfigProperty(defaultValue = "-1") int reconnectWindowSeconds,
        @ConfigProperty(defaultValue = "0.50") double fallenBehindThreshold,
        @ConfigProperty(defaultValue = "300s") Duration asyncStreamTimeout,
        @ConfigProperty(defaultValue = "100ms") Duration asyncOutputStreamFlush,
        @ConfigProperty(defaultValue = "10000") int asyncStreamBufferSize,
        @ConfigProperty(defaultValue = "10ms") Duration maxAckDelay,
        @ConfigProperty(defaultValue = "10") int maximumReconnectFailuresBeforeShutdown,
        @ConfigProperty(defaultValue = "10m") Duration minimumTimeBetweenReconnects) {}
