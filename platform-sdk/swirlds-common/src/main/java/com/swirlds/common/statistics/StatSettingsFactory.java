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

package com.swirlds.common.statistics;

/**
 * This object is used to configure general statistics settings.
 */
public final class StatSettingsFactory {

    private static StatSettings settings;

    private StatSettingsFactory() {}

    /**
     * Specify the settings that should be used for the statistics.
     */
    public static synchronized void configure(StatSettings settings) {
        StatSettingsFactory.settings = settings;
    }

    /**
     * Get the settings for statistics.
     */
    public static synchronized StatSettings get() {
        if (settings == null) {
            settings = getDefaultSettings();
        }
        return settings;
    }

    /**
     * Get default statistic settings. Useful for testing.
     */
    private static StatSettings getDefaultSettings() {
        return new StatSettings() {

            @Override
            public int getBufferSize() {
                return 100;
            }

            @Override
            public double getRecentSeconds() {
                return 63;
            }

            @Override
            public double getSkipSeconds() {
                return 60;
            }
        };
    }
}
