/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.logging.benchmark.util;

import com.swirlds.logging.benchmark.config.Constants;

/**
 * Utility class for configuring benchmark handling of timestamp management
 */
public class TimestampManagement {

    private TimestampManagement() {}

    /**
     * Reads the value from a system variable or returns {@link Constants#ENABLE_TIME_FORMATTING}
     */
    public static boolean formatTimestamp() {
        try {
            final String enableTimestampFormatting = System.getenv(Constants.ENABLE_TIME_FORMATTING_ENV);
            return enableTimestampFormatting != null ? Boolean.parseBoolean(enableTimestampFormatting)
                    : Constants.ENABLE_TIME_FORMATTING;
        } catch (Exception e) {
            return Constants.ENABLE_TIME_FORMATTING;
        }
    }
}
