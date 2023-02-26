/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
 * A temporary class to bridge circumvent the fact that the Settings class is package private
 */
public final class StaticSettingsProvider implements SettingsProvider {

    private final Settings settings = Settings.getInstance();

    private static final StaticSettingsProvider SINGLETON = new StaticSettingsProvider();

    public static StaticSettingsProvider getSingleton() {
        return SINGLETON;
    }

    private StaticSettingsProvider() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRescueChildlessInverseProbability() {
        return settings.getRescueChildlessInverseProbability();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRandomEventProbability() {
        return settings.getRandomEventProbability();
    }

    @Override
    public int getMaxEventQueueForCons() {
        return settings.getMaxEventQueueForCons();
    }

    @Override
    public int getTransactionMaxBytes() {
        return settings.getTransactionMaxBytes();
    }

    @Override
    public int getSignedStateFreq() {
        return settings.getSignedStateFreq();
    }

    @Override
    public long getDelayShuffle() {
        return settings.getDelayShuffle();
    }

    @Override
    public int getSocketIpTos() {
        return settings.getSocketIpTos();
    }

    @Override
    public int getTimeoutSyncClientSocket() {
        return settings.getTimeoutSyncClientSocket();
    }

    @Override
    public int getTimeoutSyncClientConnect() {
        return settings.getTimeoutSyncClientConnect();
    }

    @Override
    public int getTimeoutServerAcceptConnect() {
        return settings.getTimeoutServerAcceptConnect();
    }

    @Override
    public boolean isTcpNoDelay() {
        return settings.isTcpNoDelay();
    }

    /**
     * @see Settings#getThrottleTransactionQueueSize()
     */
    @Override
    public int getThrottleTransactionQueueSize() {
        return settings.getThrottleTransactionQueueSize();
    }

    @Override
    public int getMaxTransactionBytesPerEvent() {
        return settings.getMaxTransactionBytesPerEvent();
    }

    @Override
    public boolean useLoopbackIp() {
        return settings.isUseLoopbackIp();
    }

    @Override
    public int connectionStreamBufferSize() {
        return settings.getBufferSize();
    }

    @Override
    public int sleepHeartbeatMillis() {
        return settings.getSleepHeartbeat();
    }

    @Override
    public boolean isRequireStateLoad() {
        return settings.isRequireStateLoad();
    }

    @Override
    public boolean isCheckSignedStateFromDisk() {
        return settings.isCheckSignedStateFromDisk();
    }
}
