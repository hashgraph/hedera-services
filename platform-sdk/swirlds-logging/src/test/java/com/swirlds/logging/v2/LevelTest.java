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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelTest {

    @Test
    void testError() {
        // given
        final Level level = Level.ERROR;

        // then
        assertTrue(level.enabledLoggingOfLevel(Level.ERROR));
        assertFalse(level.enabledLoggingOfLevel(Level.WARN));
        assertFalse(level.enabledLoggingOfLevel(Level.INFO));
        assertFalse(level.enabledLoggingOfLevel(Level.DEBUG));
        assertFalse(level.enabledLoggingOfLevel(Level.TRACE));
        assertTrue(level.enabledLoggingOfLevel(null), "for null param it should return true");
        assertEquals("ERROR", level.name());
    }

    @Test
    void testWarn() {
        // given
        final Level level = Level.WARN;

        // then
        assertTrue(level.enabledLoggingOfLevel(Level.ERROR));
        assertTrue(level.enabledLoggingOfLevel(Level.WARN));
        assertFalse(level.enabledLoggingOfLevel(Level.INFO));
        assertFalse(level.enabledLoggingOfLevel(Level.DEBUG));
        assertFalse(level.enabledLoggingOfLevel(Level.TRACE));
        assertTrue(level.enabledLoggingOfLevel(null), "for null param it should return true");
        assertEquals("WARN", level.name());
    }

    @Test
    void testInfo() {
        // given
        final Level level = Level.INFO;

        // then
        assertTrue(level.enabledLoggingOfLevel(Level.ERROR));
        assertTrue(level.enabledLoggingOfLevel(Level.WARN));
        assertTrue(level.enabledLoggingOfLevel(Level.INFO));
        assertFalse(level.enabledLoggingOfLevel(Level.DEBUG));
        assertFalse(level.enabledLoggingOfLevel(Level.TRACE));
        assertTrue(level.enabledLoggingOfLevel(null), "for null param it should return true");
        assertEquals("INFO", level.name());
    }

    @Test
    void testDebug() {
        // given
        final Level level = Level.DEBUG;

        // then
        assertTrue(level.enabledLoggingOfLevel(Level.ERROR));
        assertTrue(level.enabledLoggingOfLevel(Level.WARN));
        assertTrue(level.enabledLoggingOfLevel(Level.INFO));
        assertTrue(level.enabledLoggingOfLevel(Level.DEBUG));
        assertFalse(level.enabledLoggingOfLevel(Level.TRACE));
        assertTrue(level.enabledLoggingOfLevel(null), "for null param it should return true");
        assertEquals("DEBUG", level.name());
    }

    @Test
    void testTrace() {
        // given
        final Level level = Level.TRACE;

        // then
        assertTrue(level.enabledLoggingOfLevel(Level.ERROR));
        assertTrue(level.enabledLoggingOfLevel(Level.WARN));
        assertTrue(level.enabledLoggingOfLevel(Level.INFO));
        assertTrue(level.enabledLoggingOfLevel(Level.DEBUG));
        assertTrue(level.enabledLoggingOfLevel(Level.TRACE));
        assertTrue(level.enabledLoggingOfLevel(null), "for null param it should return true");
        assertEquals("TRACE", level.name());
    }
}
