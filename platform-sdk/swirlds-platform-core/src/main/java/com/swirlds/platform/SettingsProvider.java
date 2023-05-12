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

package com.swirlds.platform;

/**
 * A temporary interface to bridge circumvent the fact that the Settings class is package private
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need to
 * 		use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public interface SettingsProvider {
    /**
     * Returns the inverse of a probability that we will create a child for a childless event
     */
    int getRescueChildlessInverseProbability();

    /**
     * The probability that after a sync, a node will create an event with a random other parent. The probability is
     * is 1 in X, where X is the value of randomEventProbability. A value of 0 means that a node will not create any
     * random events.
     *
     * This feature is used to get consensus on events with no descendants which are created by nodes who go offline.
     */
    int getRandomEventProbability();

    /**
     * @see Settings#maxEventQueueForCons
     */
    int getMaxEventQueueForCons();

    /**
     * @see Settings#transactionMaxBytes
     */
    int getTransactionMaxBytes();

    /**
     * @see Settings#signedStateFreq
     */
    int getSignedStateFreq();

    /**
     * @see Settings#delayShuffle
     */
    long getDelayShuffle();

    /**
     * @see Settings#socketIpTos
     */
    int getSocketIpTos();

    /**
     * @see Settings#timeoutSyncClientSocket
     */
    int getTimeoutSyncClientSocket();

    /**
     * @see Settings#timeoutSyncClientConnect
     */
    int getTimeoutSyncClientConnect();

    /**
     * @see Settings#timeoutServerAcceptConnect
     */
    int getTimeoutServerAcceptConnect();

    /**
     * @see Settings#tcpNoDelay
     */
    boolean isTcpNoDelay();

    /**
     * @see Settings#throttleTransactionQueueSize
     */
    int getThrottleTransactionQueueSize();

    /**
     * @see Settings#maxTransactionBytesPerEvent
     */
    int getMaxTransactionBytesPerEvent();

    /**
     * @see Settings#useLoopbackIp
     */
    boolean useLoopbackIp();

    /**
     * @see Settings#bufferSize
     */
    int connectionStreamBufferSize();

    /**
     * @see Settings#sleepHeartbeat
     */
    int sleepHeartbeatMillis();

    boolean isRequireStateLoad();

    boolean isCheckSignedStateFromDisk();
}
