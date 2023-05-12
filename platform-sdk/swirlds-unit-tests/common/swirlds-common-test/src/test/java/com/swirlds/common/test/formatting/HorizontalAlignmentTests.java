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

package com.swirlds.common.test.formatting;

import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_CENTER;
import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_LEFT;
import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HorizontalAlignment Tests")
public class HorizontalAlignmentTests {

    @Test
    @DisplayName("ALIGNED_LEFT Test")
    void alignedLeftTest() {
        assertEquals("", ALIGNED_LEFT.pad("", ' ', 0));
        assertEquals("   ", ALIGNED_LEFT.pad("  ", ' ', 3));
        assertEquals("abc", ALIGNED_LEFT.pad("abc", ' ', 0));
        assertEquals("abc", ALIGNED_LEFT.pad("abc", ' ', 2));
        assertEquals("abc", ALIGNED_LEFT.pad("abc", ' ', 3));
        assertEquals("abc ", ALIGNED_LEFT.pad("abc", ' ', 4));
        assertEquals("abc       ", ALIGNED_LEFT.pad("abc", ' ', 10));
        assertEquals("foo-------", ALIGNED_LEFT.pad("foo", '-', 10));
    }

    @Test
    @DisplayName("ALIGNED_RIGHT Test")
    void alignedRightTest() {
        assertEquals("", ALIGNED_RIGHT.pad("", ' ', 0));
        assertEquals("   ", ALIGNED_RIGHT.pad("  ", ' ', 3));
        assertEquals("abc", ALIGNED_RIGHT.pad("abc", ' ', 0));
        assertEquals("abc", ALIGNED_RIGHT.pad("abc", ' ', 2));
        assertEquals("abc", ALIGNED_RIGHT.pad("abc", ' ', 3));
        assertEquals(" abc", ALIGNED_RIGHT.pad("abc", ' ', 4));
        assertEquals("       abc", ALIGNED_RIGHT.pad("abc", ' ', 10));
        assertEquals("-------foo", ALIGNED_RIGHT.pad("foo", '-', 10));
    }

    @Test
    @DisplayName("ALIGNED_CENTER Test")
    void alignedCenterTest() {
        assertEquals("", ALIGNED_CENTER.pad("", ' ', 0));
        assertEquals("   ", ALIGNED_CENTER.pad("  ", ' ', 3));
        assertEquals("abc", ALIGNED_CENTER.pad("abc", ' ', 0));
        assertEquals("abc", ALIGNED_CENTER.pad("abc", ' ', 2));
        assertEquals("abc", ALIGNED_CENTER.pad("abc", ' ', 3));
        assertEquals("abc ", ALIGNED_CENTER.pad("abc", ' ', 4));
        assertEquals(" abc ", ALIGNED_CENTER.pad("abc", ' ', 5));
        assertEquals("   abc    ", ALIGNED_CENTER.pad("abc", ' ', 10));
        assertEquals("    abc    ", ALIGNED_CENTER.pad("abc", ' ', 11));
        assertEquals("---foo----", ALIGNED_CENTER.pad("foo", '-', 10));
        assertEquals("----foo----", ALIGNED_CENTER.pad("foo", '-', 11));
    }
}
