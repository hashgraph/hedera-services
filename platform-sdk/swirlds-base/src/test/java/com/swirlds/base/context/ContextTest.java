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

package com.swirlds.base.context;

import com.swirlds.base.context.internal.GlobalContext;
import com.swirlds.base.context.internal.ThreadLocalContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ContextTest {

    @Test
    void testOnlyOneGlobalContext() {
        // given
        Context context = Context.getGlobalContext();

        // when
        Context globalContext = GlobalContext.getInstance();

        // then
        Assertions.assertSame(context, globalContext);
    }

    @Test
    void testOnlyOneThreadLocalContext() {
        // given
        Context context = Context.getThreadLocalContext();

        // when
        Context threadLocalContext = ThreadLocalContext.getInstance();

        // then
        Assertions.assertSame(context, threadLocalContext);
    }
}
