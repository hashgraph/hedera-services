/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.spi.workflows.PreHandleContext;

/**
 * Basic assertions for the pre-handle context.
 */
public class MetaAssertion {
    /**
     * Basic pre-handle context assertions.
     * @param context the context
     * @param keysSize the size of the keys
     */
    public static void basicContextAssertions(final PreHandleContext context, final int keysSize) {
        assertEquals(keysSize, context.requiredNonPayerKeys().size());
    }
}
