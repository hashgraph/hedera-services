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

package com.swirlds.logging.v2.internal;

import com.swirlds.base.context.internal.GlobalContext;
import com.swirlds.base.context.internal.ThreadLocalContext;
import com.swirlds.logging.v2.Level;
import com.swirlds.logging.v2.Logger;
import com.swirlds.logging.v2.Marker;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ConsoleLogger implements Logger {

    private final String name;

    private final DateTimeFormatter formatter;

    private final Marker marker;

    private Map<String, String> context;

    private ConsoleLogger(String name, final Marker marker, Map<String, String> context) {
        this.name = name;
        this.marker = marker;
        this.context = new ConcurrentHashMap<>(context);
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    public ConsoleLogger(String name) {
        this(name, null, Map.of());
    }

    private void logImpl(Level level, String message, final Throwable throwable) {
        String timeStamp = LocalDateTime.now().format(formatter);
        String threadName = Thread.currentThread().getName();
        StringBuffer sb = new StringBuffer();
        sb.append(timeStamp)
                .append(" ")
                .append(level)
                .append(" ")
                .append("[")
                .append(threadName)
                .append("]")
                .append(" ")
                .append(name)
                .append(" - ")
                .append(TextEffect.BOLD.apply(message));

        if (marker != null) {
            sb.append(" - M:").append(marker.getName());
        }

        Map<String, String> mergedContext = new HashMap<>();
        mergedContext.putAll(GlobalContext.getContextMap());
        mergedContext.putAll(ThreadLocalContext.getContextMap());
        mergedContext.putAll(context);

        if (!mergedContext.isEmpty()) {
            sb.append(" - C:").append(mergedContext);
        }

        if (throwable != null) {
            sb.append(System.lineSeparator());
            StringBuffer stackTraceBuffer = new StringBuffer();
            PrintStream printStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    stackTraceBuffer.append((char) b);
                }
            });
            throwable.printStackTrace(printStream);
            sb.append(TextEffect.ITALIC.apply(stackTraceBuffer.toString()));
        }

        if (level == Level.ERROR) {
            System.out.println(TextEffect.RED.apply(sb.toString()));
        } else if (level == Level.WARN) {
            System.out.println(TextEffect.BRIGHT_RED.apply(sb.toString()));
        } else {
            System.out.println(sb.toString());
        }
    }

    @Override
    public void log(Level level, String message) {
        logImpl(level, message, null);
    }

    @Override
    public void log(Level level, Supplier<String> messageSupplier) {
        logImpl(level, messageSupplier.get(), null);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        logImpl(level, message, throwable);
    }

    @Override
    public void log(Level level, Supplier<String> messageSupplier, Throwable throwable) {
        logImpl(level, messageSupplier.get(), throwable);
    }

    private String formatMessage(String message, Object... args) {
        for (Object arg : args) {
            message = message.replaceFirst("\\{\\}", arg.toString());
        }
        return message;
    }

    @Override
    public void log(Level level, String message, Object... args) {
        logImpl(level, formatMessage(message, args), null);
    }

    @Override
    public void log(Level level, String message, Object arg) {
        logImpl(level, formatMessage(message, arg), null);
    }

    @Override
    public void log(Level level, String message, Object arg1, Object arg2) {
        logImpl(level, formatMessage(message, arg1, arg2), null);
    }

    @Override
    public void log(Level level, String message, Throwable throwable, Object... args) {
        logImpl(level, formatMessage(message, args), throwable);
    }

    @Override
    public void log(Level level, String message, Throwable throwable, Object arg1) {
        logImpl(level, formatMessage(message, arg1), throwable);
    }

    @Override
    public void log(Level level, String message, Throwable throwable, Object arg1, Object arg2) {
        logImpl(level, formatMessage(message, arg1, arg2), throwable);
    }

    @Override
    public Logger withMarker(String markerName) {
        return new ConsoleLogger(name, new MarkerImpl(markerName, marker), context);
    }

    @Override
    public Logger withContext(String key, String value) {
        Map<String, String> newContext = new ConcurrentHashMap<>(context);
        newContext.put(key, value);
        return new ConsoleLogger(name, marker, newContext);
    }

    @Override
    public Logger withContext(String key, String... values) {
        Map<String, String> newContext = new ConcurrentHashMap<>(context);
        newContext.put(key, String.join(",", values));
        return new ConsoleLogger(name, marker, newContext);
    }

    @Override
    public Logger withContext(String key, Supplier<String> value) {
        return withContext(key, value.get());
    }
}
