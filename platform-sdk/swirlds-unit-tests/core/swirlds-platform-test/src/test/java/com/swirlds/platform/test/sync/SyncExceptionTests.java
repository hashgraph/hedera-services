/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.Connection;
import com.swirlds.platform.sync.SyncException;
import org.junit.jupiter.api.Test;

class SyncExceptionTests {

    private static final String msg = "hello swirlds";
    private static final String description = "connection description";

    @Test
    void testMessage() {
        SyncException e = new SyncException(msg);
        assertEquals(msg, e.getMessage(), "Message does not match the message provided.");
    }

    @Test
    void testConnection() {
        Connection conn = mock(Connection.class);
        when(conn.getDescription()).thenReturn(description);

        SyncException e = new SyncException(conn, msg);
        assertTrue(e.getMessage().contains(description), "Description does not match the description provided.");
        assertTrue(e.getMessage().contains(msg), "Message does not match the message provided.");
        assertNull(e.getCause(), "Cause should be null when none is provided.");
    }

    @Test
    void testCause() {
        Connection conn = mock(Connection.class);
        when(conn.getDescription()).thenReturn(description);
        Throwable t = mock(Throwable.class);
        SyncException e = new SyncException(conn, msg, t);

        assertEquals(t, e.getCause(), "Cause does not match the cause provided.");
        assertTrue(e.getMessage().contains(description), "Description does not match the description provided.");
        assertTrue(e.getMessage().contains(msg), "Message does not match the message provided.");
    }
}
