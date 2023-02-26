/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

public final class ExceptionAssertions {
    public static final String CAUSE_MESSAGE = "cause";
    public static final Throwable CAUSE = new Exception(CAUSE_MESSAGE);
    public static final String MESSAGE = "message";

    private ExceptionAssertions() {}

    public static void assertExceptionSame(final Exception e, final String message, final Throwable cause) {
        assertEquals(message, e.getMessage(), "Expected message does not equal the actual message!");
        assertSame(cause, e.getCause(), "The cause is not the instance that was expected!");
    }

    public static void assertExceptionContains(final Exception e, final List<String> contains, final Throwable cause) {
        for (String s : contains) {
            assertTrue(e.getMessage().contains(s), "The message does not contain the expected strings!");
        }
        assertSame(cause, e.getCause(), "The cause is not the instance that was expected!");
    }
}
