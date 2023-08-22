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

package com.swirlds.logging.v2;

import java.util.function.Supplier;

public interface Logger {

    default void error(String message) {
        log(Level.ERROR, message);
    }

    default void error(Supplier<String> messageSupplier) {
        log(Level.ERROR, messageSupplier);
    }

    default void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }

    default void error(Supplier<String> messageSupplier, Throwable throwable) {
        log(Level.ERROR, messageSupplier, throwable);
    }

    default void error(String message, Object... args) {
        log(Level.ERROR, message, args);
    }

    default void error(String message, Object arg) {
        log(Level.ERROR, message, arg);
    }

    default void error(String message, Object arg1, Object arg2) {
        log(Level.ERROR, message, arg1, arg2);
    }

    default void error(String message, Throwable throwable, Object... args) {
        log(Level.ERROR, message, throwable, args);
    }

    default void error(String message, Throwable throwable, Object arg1) {
        log(Level.ERROR, message, throwable, arg1);
    }

    default void error(String message, Throwable throwable, Object arg1, Object arg2) {
        log(Level.ERROR, message, throwable, arg1, arg2);
    }

    default void warn(String message) {
        log(Level.WARN, message);
    }

    default void warn(Supplier<String> messageSupplier) {
        log(Level.WARN, messageSupplier);
    }

    default void warn(String message, Throwable throwable) {
        log(Level.WARN, message, throwable);
    }

    default void warn(Supplier<String> messageSupplier, Throwable throwable) {
        log(Level.WARN, messageSupplier, throwable);
    }

    default void warn(String message, Object... args) {
        log(Level.WARN, message, args);
    }

    default void warn(String message, Object arg) {
        log(Level.WARN, message, arg);
    }

    default void warn(String message, Object arg1, Object arg2) {
        log(Level.WARN, message, arg1, arg2);
    }

    default void warn(String message, Throwable throwable, Object... args) {
        log(Level.WARN, message, throwable, args);
    }

    default void warn(String message, Throwable throwable, Object arg1) {
        log(Level.WARN, message, throwable, arg1);
    }

    default void warn(String message, Throwable throwable, Object arg1, Object arg2) {
        log(Level.WARN, message, throwable, arg1, arg2);
    }

    default void info(String message) {
        log(Level.INFO, message);
    }

    default void info(Supplier<String> messageSupplier) {
        log(Level.INFO, messageSupplier);
    }

    default void info(String message, Throwable throwable) {
        log(Level.INFO, message, throwable);
    }

    default void info(Supplier<String> messageSupplier, Throwable throwable) {
        log(Level.INFO, messageSupplier, throwable);
    }

    default void info(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    default void info(String message, Object arg) {
        log(Level.INFO, message, arg);
    }

    default void info(String message, Object arg1, Object arg2) {
        log(Level.INFO, message, arg1, arg2);
    }

    default void info(String message, Throwable throwable, Object... args) {
        log(Level.INFO, message, throwable, args);
    }

    default void info(String message, Throwable throwable, Object arg1) {
        log(Level.INFO, message, throwable, arg1);
    }

    default void info(String message, Throwable throwable, Object arg1, Object arg2) {
        log(Level.INFO, message, throwable, arg1, arg2);
    }

    default void debug(String message) {
        log(Level.DEBUG, message);
    }

    default void debug(Supplier<String> messageSupplier) {
        log(Level.DEBUG, messageSupplier);
    }

    default void debug(String message, Throwable throwable) {
        log(Level.DEBUG, message, throwable);
    }

    default void debug(Supplier<String> messageSupplier, Throwable throwable) {
        log(Level.DEBUG, messageSupplier, throwable);
    }

    default void debug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    default void debug(String message, Object arg) {
        log(Level.DEBUG, message, arg);
    }

    default void debug(String message, Object arg1, Object arg2) {
        log(Level.DEBUG, message, arg1, arg2);
    }

    default void debug(String message, Throwable throwable, Object... args) {
        log(Level.DEBUG, message, throwable, args);
    }

    default void debug(String message, Throwable throwable, Object arg1) {
        log(Level.DEBUG, message, throwable, arg1);
    }

    default void debug(String message, Throwable throwable, Object arg1, Object arg2) {
        log(Level.DEBUG, message, throwable, arg1, arg2);
    }

    default void trace(String message) {
        log(Level.TRACE, message);
    }

    default void trace(Supplier<String> messageSupplier) {
        log(Level.TRACE, messageSupplier);
    }

    default void trace(String message, Throwable throwable) {
        log(Level.TRACE, message, throwable);
    }

    default void trace(Supplier<String> messageSupplier, Throwable throwable) {
        log(Level.TRACE, messageSupplier, throwable);
    }

    default void trace(String message, Object... args) {
        log(Level.TRACE, message, args);
    }

    default void trace(String message, Object arg) {
        log(Level.TRACE, message, arg);
    }

    default void trace(String message, Object arg1, Object arg2) {
        log(Level.TRACE, message, arg1, arg2);
    }

    default void trace(String message, Throwable throwable, Object... args) {
        log(Level.TRACE, message, throwable, args);
    }

    default void trace(String message, Throwable throwable, Object arg1) {
        log(Level.TRACE, message, throwable, arg1);
    }

    default void trace(String message, Throwable throwable, Object arg1, Object arg2) {
        log(Level.TRACE, message, throwable, arg1, arg2);
    }

    void log(Level level, String message);

    void log(Level level, Supplier<String> messageSupplier);

    void log(Level level, String message, Throwable throwable);

    void log(Level level, Supplier<String> messageSupplier, Throwable throwable);

    void log(Level level, String message, Object... args);

    void log(Level level, String message, Object arg);

    void log(Level level, String message, Object arg1, Object arg2);

    void log(Level level, String message, Throwable throwable, Object... args);

    void log(Level level, String message, Throwable throwable, Object arg1);

    void log(Level level, String message, Throwable throwable, Object arg1, Object arg2);

    Logger withMarker(String markerName);

    Logger withContext(String key, String value);

    Logger withContext(String key, String... values);

    default Logger withContext(String key, int value) {
        return withContext(key, Integer.toString(value));
    }

    default Logger withContext(String key, int... values) {
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = Integer.toString(values[i]);
        }
        return withContext(key, stringValues);
    }

    default Logger withContext(String key, long value) {
        return withContext(key, Long.toString(value));
    }

    default Logger withContext(String key, long... values) {
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = Long.toString(values[i]);
        }
        return withContext(key, stringValues);
    }

    default Logger withContext(String key, double value) {
        return withContext(key, Double.toString(value));
    }

    default Logger withContext(String key, double... values) {
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = Double.toString(values[i]);
        }
        return withContext(key, stringValues);
    }

    default Logger withContext(String key, float value) {
        return withContext(key, Float.toString(value));
    }

    default Logger withContext(String key, float... values) {
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = Float.toString(values[i]);
        }
        return withContext(key, stringValues);
    }

    default Logger withContext(String key, boolean value) {
        return withContext(key, Boolean.toString(value));
    }

    default Logger withContext(String key, boolean... value) {
        String[] stringValues = new String[value.length];
        for (int i = 0; i < value.length; i++) {
            stringValues[i] = Boolean.toString(value[i]);
        }
        return withContext(key, stringValues);
    }

    Logger withContext(String key, Supplier<String> value);
}
