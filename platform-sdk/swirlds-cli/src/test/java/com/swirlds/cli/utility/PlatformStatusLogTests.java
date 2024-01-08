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

package com.swirlds.cli.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.cli.logging.PlatformStatusLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PlatformStatusLog Tests
 */
class PlatformStatusLogTests {
    public static final String testString =
            """
                    Platform spent 441.0 ms in STARTING_UP. Now in REPLAYING_EVENTS {"oldStatus":"STARTING_UP","newStatus":"REPLAYING_EVENTS"} [com.swirlds.logging.payloads.PlatformStatusPayload]
                    """;

    @Test
    @DisplayName("Test splitting a status log")
    void splitStatusLog() {
        final PlatformStatusLog statusLog = new PlatformStatusLog(testString.trim());

        assertEquals("STARTING_UP", statusLog.getPreviousStatus());
        assertEquals("REPLAYING_EVENTS", statusLog.getNewStatus());
        assertEquals("441.0 ms", statusLog.getDuration());
    }
}
