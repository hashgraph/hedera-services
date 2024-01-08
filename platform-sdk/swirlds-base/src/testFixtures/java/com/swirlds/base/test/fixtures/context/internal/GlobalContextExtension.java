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

package com.swirlds.base.test.fixtures.context.internal;

import com.swirlds.base.context.internal.GlobalContext;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension to clear the global context before and after each test.
 */
public class GlobalContextExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ThreadLocal<Map<String, String>> savedSate = new ThreadLocal<>();

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        savedSate.set(GlobalContext.getContextMap());
        GlobalContext.getInstance().clear();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        GlobalContext.getInstance().clear();
        Optional.ofNullable(savedSate.get()).ifPresent(map -> map.forEach(GlobalContext.getInstance()::add));
    }
}
