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

package com.swirlds.base.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.base.context.internal.ThreadLocalContext;
import com.swirlds.base.test.fixtures.context.WithContext;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

@WithContext
public class ThreadLocalContextTest {

    @Test
    void testNullKeyOrValue() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();

        // then
        assertThrows(NullPointerException.class, () -> context.add(null, "value"));
        assertThrows(NullPointerException.class, () -> context.add(null, 1));
        assertThrows(NullPointerException.class, () -> context.add(null, 1L));
        assertThrows(NullPointerException.class, () -> context.add(null, 1.0D));
        assertThrows(NullPointerException.class, () -> context.add(null, 1.0F));
        assertThrows(NullPointerException.class, () -> context.add(null, true));
        assertThrows(NullPointerException.class, () -> context.add("foo", null));
        assertThrows(NullPointerException.class, () -> context.add(null, null));
    }

    @Test
    void testAllPut() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();

        // when
        context.add("key-string", "value");
        context.add("key-int", 1);
        context.add("key-long", 1L);
        context.add("key-double", 1.0D);
        context.add("key-float", 1.0F);
        context.add("key-boolean", true);

        // then
        final Map<String, String> contextMap = context.getContextMap();
        assertEquals(6, contextMap.size());
        assertEquals("value", contextMap.get("key-string"));
        assertEquals("1", contextMap.get("key-int"));
        assertEquals("1", contextMap.get("key-long"));
        assertEquals("1.0", contextMap.get("key-double"));
        assertEquals("1.0", contextMap.get("key-float"));
        assertEquals("true", contextMap.get("key-boolean"));
    }

    @Test
    void testOverwrite() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();

        // when
        context.add("key", "a");
        context.add("key", "b");
        context.add("key", "c");

        // then
        final Map<String, String> contextMap = context.getContextMap();
        assertEquals(1, contextMap.size());
        assertEquals("c", contextMap.get("key"));
    }

    @Test
    void testRemove() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();

        // when
        context.add("key", "a");
        context.remove("key");

        // then
        final Map<String, String> contextMap = context.getContextMap();
        assertEquals(0, contextMap.size());
    }

    @Test
    void testRemoveNullKey() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();

        // then
        assertThrows(NullPointerException.class, () -> context.remove(null));
    }

    @Test
    void testClear() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();
        context.add("key", "a");
        context.add("key-2", "a");

        // when
        context.clear();

        // then
        final Map<String, String> contextMap = context.getContextMap();
        assertEquals(0, contextMap.size());
    }

    @Test
    void testAutocloseable() {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();
        AutoCloseable closeable = context.add("key", "a");

        // when
        assertDoesNotThrow(() -> closeable.close());

        // then
        final Map<String, String> contextMap = context.getContextMap();
        assertEquals(0, contextMap.size());
    }

    @Test
    void testOnDifferentThreads() throws Exception {
        // given
        ThreadLocalContext context = ThreadLocalContext.getInstance();
        ExecutorService executor1 = Executors.newSingleThreadExecutor();
        ExecutorService executor2 = Executors.newSingleThreadExecutor();
        ExecutorService executor3 = Executors.newSingleThreadExecutor();

        // when
        final Map<String, String> mapFromExecutor1 = executor1
                .submit(() -> {
                    context.add("key", "a1");
                    return context.getContextMap();
                })
                .get();

        final Map<String, String> mapFromExecutor2 = executor2
                .submit(() -> {
                    context.add("key", "a2");
                    return context.getContextMap();
                })
                .get();
        final Map<String, String> mapFromExecutor3 = executor3
                .submit(() -> {
                    return context.getContextMap();
                })
                .get();

        // then
        assertNotNull(mapFromExecutor1);
        assertEquals(1, mapFromExecutor1.size());
        assertEquals("a1", mapFromExecutor1.get("key"));

        assertNotNull(mapFromExecutor2);
        assertEquals(1, mapFromExecutor2.size());
        assertEquals("a2", mapFromExecutor2.get("key"));

        assertNotNull(mapFromExecutor3);
        assertEquals(0, mapFromExecutor3.size());
    }
}
