/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for sockets
 *
 * @param ipTos                      The IP_TOS to set for a socket, from 0 to 255, or -1 to not set one. This number
 *                                   (if not -1) will be part of every TCP/IP packet, and is normally ignored by
 *                                   internet routers, but it is possible to make routers change their handling of
 *                                   packets based on this number, such as for providing different Quality of Service
 *                                   (QoS). <a href="https://en.wikipedia.org/wiki/Type_of_service">Type of Service</a>
 * @param bufferSize                 for BufferedInputStream and BufferedOutputStream for syncing
 * @param timeoutSyncClientSocket    timeout when waiting for data
 * @param timeoutSyncClientConnect   timeout when establishing a connection
 * @param timeoutServerAcceptConnect timeout when server is waiting for another member to create a connection
 * @param useLoopbackIp              should be set to true when using the internet simulator
 * @param tcpNoDelay                 if true, then Nagel's algorithm is disabled, which helps latency, hurts bandwidth
 *                                   usage
 * @param gzipCompression            whether to use gzip compression over the network
 */
@ConfigData("socket")
public record SocketConfig(
        @ConfigProperty(defaultValue = "-1") int ipTos,
        @ConfigProperty(defaultValue = "8192") int bufferSize,
        @ConfigProperty(defaultValue = "5000") int timeoutSyncClientSocket,
        @ConfigProperty(defaultValue = "5000") int timeoutSyncClientConnect,
        @ConfigProperty(defaultValue = "5000") int timeoutServerAcceptConnect,
        @ConfigProperty(defaultValue = "false") boolean useLoopbackIp,
        @ConfigProperty(defaultValue = "true") boolean tcpNoDelay,
        @ConfigProperty(defaultValue = "false") boolean gzipCompression) {}
