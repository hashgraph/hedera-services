package com.swirlds.logging.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelTest {

    @Test
    void testError() {
        //given
        final Level level = Level.ERROR;

        //then
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
        //given
        final Level level = Level.WARN;

        //then
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
        //given
        final Level level = Level.INFO;

        //then
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
        //given
        final Level level = Level.DEBUG;

        //then
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
        //given
        final Level level = Level.TRACE;

        //then
        assertTrue(level.enabledLoggingOfLevel(Level.ERROR));
        assertTrue(level.enabledLoggingOfLevel(Level.WARN));
        assertTrue(level.enabledLoggingOfLevel(Level.INFO));
        assertTrue(level.enabledLoggingOfLevel(Level.DEBUG));
        assertTrue(level.enabledLoggingOfLevel(Level.TRACE));
        assertTrue(level.enabledLoggingOfLevel(null), "for null param it should return true");
        assertEquals("TRACE", level.name());
    }

}