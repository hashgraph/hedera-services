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

package com.swirlds.common.concurrent;

import com.swirlds.base.context.Context;
import com.swirlds.common.concurrent.internal.ConcurrentFactoryImpl;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import java.lang.Thread.UncaughtExceptionHandler;

public class ConcurrentFactoryPlatformSample {

    private static final Logger log = Loggers.getLogger("PlatformUncaughtExceptionHandler");

    public static void main(String[] args) {
        final String platformId = "platform-123";
        final ConcurrentFactory factory = getConcurrentFactoryForPlatform(platformId);

        factory.createExecutorService(1).submit(() -> {
            throw new RuntimeException("This is a sample exception");
        });
    }

    public static ConcurrentFactory getConcurrentFactoryForPlatform(final String platformId) {
        final String groupName = "platform-group";
        final Runnable onStartup = () -> {
            Context.getThreadLocalContext().add("platformId", platformId);
        };
        final UncaughtExceptionHandler handler = (t, e) -> {
            log.withContext("platformId", platformId).error("Uncaught exception in thread: " + t.getName(), e);
        };
        return ConcurrentFactoryImpl.create(groupName, onStartup, handler);
    }
}
