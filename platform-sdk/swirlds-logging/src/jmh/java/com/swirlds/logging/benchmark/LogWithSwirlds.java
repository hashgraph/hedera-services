/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.benchmark;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.benchmark.util.BenchmarkUtils;

public class LogWithSwirlds implements Runnable {

    private final Logger logger;

    public LogWithSwirlds(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "L0, Hello world!");
        logger.log(Level.INFO, "L1, A quick brown fox jumps over the lazy dog.");
        logger.log(Level.INFO, "L2, Hello world!", BenchmarkUtils.THROWABLE);
        logger.log(Level.INFO, "L3, Hello {}!", "placeholder");
        logger.withContext("key", "value").log(Level.INFO, "L4, Hello world!");
        logger.withMarker("marker").log(Level.INFO, "L5, Hello world!");
        logger.withContext("user-id", BenchmarkUtils.USER_1).log(Level.INFO, "L6, Hello world!");
        logger.withContext("user-id", BenchmarkUtils.USER_2)
                .log(Level.INFO, "L7, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!", 1, 2, 3, 4, 5, 6, 7, 8, 9);
        logger.withContext("user-id", BenchmarkUtils.USER_3)
                .withContext("key", "value")
                .log(Level.INFO, "L8, Hello world!");
        logger.withMarker("marker").log(Level.INFO, "L9, Hello world!");
        logger.withMarker("marker1").withMarker("marker2").log(Level.INFO, "L10, Hello world!");
        logger.withContext("key", "value")
                .withMarker("marker1")
                .withMarker("marker2")
                .log(Level.INFO, "L11, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!", 1, 2, 3, 4, 5, 6, 7, 8, 9);
        logger.log(Level.INFO, "L12, Hello world!", BenchmarkUtils.DEEP_THROWABLE);
    }
}
