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

import com.swirlds.base.context.internal.GlobalContext;
import com.swirlds.base.test.fixtures.context.WithContext;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@WithContext
public class GlobalContextTest {

    @Test
    void testNullKeyOrValue() {
        // given
        GlobalContext context = GlobalContext.getInstance();

        // then
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, "value"));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, 1));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, 1L));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, 1.0D));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, 1.0F));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, true));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose("foo", null));
        Assertions.assertThrows(NullPointerException.class, () -> context.addWithRemovalOnClose(null, null));
    }

    @Test
    void testAllPut() {
        // given
        GlobalContext context = GlobalContext.getInstance();

        // when
        context.addWithRemovalOnClose("key-string", "value");
        context.addWithRemovalOnClose("key-int", 1);
        context.addWithRemovalOnClose("key-long", 1L);
        context.addWithRemovalOnClose("key-double", 1.0D);
        context.addWithRemovalOnClose("key-float", 1.0F);
        context.addWithRemovalOnClose("key-boolean", true);

        // then
        final Map<String, String> contextMap = context.getContextMap();
        Assertions.assertEquals(6, contextMap.size());
        Assertions.assertEquals("value", contextMap.get("key-string"));
        Assertions.assertEquals("1", contextMap.get("key-int"));
        Assertions.assertEquals("1", contextMap.get("key-long"));
        Assertions.assertEquals("1.0", contextMap.get("key-double"));
        Assertions.assertEquals("1.0", contextMap.get("key-float"));
        Assertions.assertEquals("true", contextMap.get("key-boolean"));
    }

    @Test
    void testOverwrite() {
        // given
        GlobalContext context = GlobalContext.getInstance();

        // when
        context.addWithRemovalOnClose("key", "a");
        context.addWithRemovalOnClose("key", "b");
        context.addWithRemovalOnClose("key", "c");

        // then
        final Map<String, String> contextMap = context.getContextMap();
        Assertions.assertEquals(1, contextMap.size());
        Assertions.assertEquals("c", contextMap.get("key"));
    }

    @Test
    void testRemove() {
        // given
        GlobalContext context = GlobalContext.getInstance();

        // when
        context.addWithRemovalOnClose("key", "a");
        context.remove("key");

        // then
        final Map<String, String> contextMap = context.getContextMap();
        Assertions.assertEquals(0, contextMap.size());
    }

    @Test
    void testRemoveNullKey() {
        // given
        GlobalContext context = GlobalContext.getInstance();

        // then
        Assertions.assertThrows(NullPointerException.class, () -> context.remove(null));
    }

    @Test
    void testClear() {
        // given
        GlobalContext context = GlobalContext.getInstance();
        context.addWithRemovalOnClose("key", "a");
        context.addWithRemovalOnClose("key-2", "a");

        // when
        context.clear();

        // then
        final Map<String, String> contextMap = context.getContextMap();
        Assertions.assertEquals(0, contextMap.size());
    }

    @Test
    void testAutocloseable() {
        // given
        GlobalContext context = GlobalContext.getInstance();
        AutoCloseable closeable = context.addWithRemovalOnClose("key", "a");

        // when
        Assertions.assertDoesNotThrow(() -> closeable.close());

        // then
        final Map<String, String> contextMap = context.getContextMap();
        Assertions.assertEquals(0, contextMap.size());
    }
}
