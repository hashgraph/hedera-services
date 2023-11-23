/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.api.internal.event;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import com.swirlds.logging.api.extensions.event.LogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * A log event factory that reuses the same log event instance for every log statement on a thread to avoid creating new
 * log event instances.
 */
public class ReuseableLogEventFactory implements LogEventFactory {

    /**
     * The thread local that holds the log event.
     */
    private final ThreadLocal<MutableLogEvent> threadLocal = ThreadLocal.withInitial(() -> new MutableLogEvent());

    @NonNull
    @Override
    public LogEvent createLogEvent(
            @NonNull Level level,
            @NonNull String loggerName,
            @NonNull String threadName,
            @NonNull long timestamp,
            @NonNull LogMessage message,
            @Nullable Throwable throwable,
            @Nullable Marker marker,
            @NonNull Map<String, String> context) {
        final MutableLogEvent mutableLogEvent = threadLocal.get();
        mutableLogEvent.update(level, loggerName, threadName, timestamp, message, throwable, marker, context);
        return mutableLogEvent;
    }
}
