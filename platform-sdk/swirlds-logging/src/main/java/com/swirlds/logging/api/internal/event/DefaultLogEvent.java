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

package com.swirlds.logging.api.internal.event;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * A log event that is passed to the {@link LogEventConsumer} for processing.
 *
 * @param level      The log level
 * @param loggerName The name of the logger
 * @param threadName The name of the thread
 * @param timestamp  The timestamp of the log event
 * @param message    The log message (this is not a String since the message can be parameterized. See
 *                   {@link LogMessage} for more details).
 * @param throwable  The throwable
 * @param marker     The marker
 * @param context    The context
 */
public record DefaultLogEvent(
        @NonNull Level level,
        @NonNull String loggerName,
        @NonNull String threadName,
        long timestamp,
        @NonNull LogMessage message,
        @Nullable Throwable throwable,
        @Nullable Marker marker,
        @NonNull Map<String, String> context)
        implements LogEvent {}
