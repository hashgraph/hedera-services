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

package com.swirlds.logging.log4j.factory;

import static com.swirlds.logging.log4j.factory.BaseLogger.convertLevel;

import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.AbstractLogger;

public class EmergencyBaseLogger extends AbstractLogger {
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    private static final class InstanceHolder {
        private static final EmergencyBaseLogger INSTANCE = new EmergencyBaseLogger();
    }

    public static EmergencyBaseLogger getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, Message message, Throwable t) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, CharSequence message, Throwable t) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, Object message, Throwable t) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message, Throwable t) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message, Object... params) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message, Object p0) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2) {
        return true;
    }

    @Override
    public boolean isEnabled(Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        return true;
    }

    @Override
    public boolean isEnabled(
            Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        return true;
    }

    @Override
    public boolean isEnabled(
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5) {
        return true;
    }

    @Override
    public boolean isEnabled(
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6) {
        return true;
    }

    @Override
    public boolean isEnabled(
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7) {
        return true;
    }

    @Override
    public boolean isEnabled(
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7,
            Object p8) {
        return true;
    }

    @Override
    public boolean isEnabled(
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7,
            Object p8,
            Object p9) {
        return true;
    }

    @Override
    public void logMessage(String fqcn, Level level, Marker marker, Message message, Throwable t) {
        EMERGENCY_LOGGER.log(convertLevel(level), message.getFormattedMessage(), t);
    }

    @Override
    public Level getLevel() {
        return Level.TRACE;
    }
}
