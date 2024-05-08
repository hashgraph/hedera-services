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

package com.swirlds.common.context.internal;

import com.swirlds.logging.legacy.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple uncaught exception handler that logs the exception and rethrows it.
 */
public class PlatformUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger logger = LogManager.getLogger(PlatformUncaughtExceptionHandler.class);

    private static final class InstanceHolder {
        private static final PlatformUncaughtExceptionHandler INSTANCE = new PlatformUncaughtExceptionHandler();
    }

    private PlatformUncaughtExceptionHandler() {}

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.error(LogMarker.EXCEPTION.getMarker(), "Uncaught exception in platform thread: " + t.getName(), e);
        throw new UncaughtPlatformException(e);
    }

    /**
     * Returns the singleton instance of the uncaught exception handler.
     *
     * @return the singleton instance of the uncaught exception handler
     */
    public static Thread.UncaughtExceptionHandler getInstance() {
        return InstanceHolder.INSTANCE;
    }
}
