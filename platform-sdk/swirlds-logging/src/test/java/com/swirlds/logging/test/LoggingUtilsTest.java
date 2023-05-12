/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.test;

import static com.swirlds.logging.LoggingUtils.plural;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Logging Utils Test")
class LoggingUtilsTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.LOGGING)
    @DisplayName("Test Plural")
    void testPlural() {
        assertEquals("dogs", plural(0, "dog"), "expected plural");
        assertEquals("dog", plural(1, "dog"), "expected singular");
        assertEquals("dogs", plural(2, "dog"), "expected plural");

        assertEquals("geese", plural(0, "goose", "geese"), "expected plural");
        assertEquals("goose", plural(1, "goose", "geese"), "expected singular");
        assertEquals("geese", plural(2, "goose", "geese"), "expected plural");
    }
}
