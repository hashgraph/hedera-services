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

package com.swirlds.cli.utility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for the SquelchSpam class
 */
@DisplayName("SquelchSpam Tests")
class SquelchSpamTests {

    private final String startOfSpam = "] Bad JNI lookup accessibilityHitTest";
    private final String endOfSpam = "Exception in thread \"AppKit Thread\" java.lang.NoSuchMethodError: accessibilityHitTest";

    @BeforeEach()
    void reset() {
        SquelchSpam.resetSpamStatus();
    }

    @Test
    @DisplayName("Test line is not spam")
    public void lineIsNotSpam() {
        assertFalse(SquelchSpam.lineIsSpam("This is a line outside spam."));
    }

    @Test
    @DisplayName("Test line is start of spam")
    public void lineIsStartOfSpam() {
        assertTrue(SquelchSpam.lineIsSpam(startOfSpam));
    }

    @Test
    @DisplayName("Test line is in middle of spam")
    public void lineIsMiddleOfSpam() {
        assertTrue(SquelchSpam.lineIsSpam(startOfSpam));
        assertTrue(SquelchSpam.lineIsSpam("This line is in middle of spam."));
    }

    @Test
    @DisplayName("Test line is end of spam")
    public void lineIsEndOfSpam() {
        assertTrue(SquelchSpam.lineIsSpam(startOfSpam));
        assertTrue(SquelchSpam.lineIsSpam("This line is in middle of spam."));
        assertTrue(SquelchSpam.lineIsSpam(endOfSpam));
    }
}
