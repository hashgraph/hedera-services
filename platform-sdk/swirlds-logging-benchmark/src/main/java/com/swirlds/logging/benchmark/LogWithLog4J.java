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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;

public class LogWithLog4J implements Runnable {

    private final Logger logger;

    private final Marker marker1 = MarkerManager.getMarker("marker");
    private final Marker marker2 = MarkerManager.getMarker("marker2", marker1);

    public LogWithLog4J(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "L0, Hello world!");
        logger.log(Level.INFO, "L1, A quick brown fox jumps over the lazy dog.");
        logger.log(Level.INFO, "L2, Hello world!", BenchmarkUtils.THROWABLE);
        logger.log(Level.INFO, "L3, Hello {}!", "placeholder");

        ThreadContext.put("key", "value");
        logger.log(Level.INFO, "L4, Hello world!");
        ThreadContext.clearAll();

        logger.log(Level.INFO, marker1, "L5, Hello world!");

        ThreadContext.put("user-id", BenchmarkUtils.USER_1);
        logger.log(Level.INFO, "L6, Hello world!");
        ThreadContext.clearAll();

        ThreadContext.put("user-id", BenchmarkUtils.USER_2);
        logger.log(Level.INFO, "L7, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!", 1, 2, 3, 4, 5, 6, 7, 8, 9);
        ThreadContext.clearAll();

        ThreadContext.put("key", "value");
        ThreadContext.put("user-id", BenchmarkUtils.USER_3);
        logger.log(Level.INFO, "L8, Hello world!");
        ThreadContext.clearAll();

        logger.log(Level.INFO, marker1, "L9, Hello world!");
        logger.log(Level.INFO, marker2, "L10, Hello world!");

        ThreadContext.put("key", "value");
        logger.log(Level.INFO, marker2, "L11, Hello {}, {}, {}, {}, {}, {}, {}, {}, {}!", 1, 2, 3, 4, 5, 6, 7, 8, 9);
        ThreadContext.clearAll();

        logger.log(Level.INFO, "L12, Hello world!", BenchmarkUtils.DEEP_THROWABLE);
    }
}
