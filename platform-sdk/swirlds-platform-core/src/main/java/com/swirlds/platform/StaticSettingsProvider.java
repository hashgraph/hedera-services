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

    @Override
    public int getTransactionMaxBytes() {
        return settings.getTransactionMaxBytes();
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
}
