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

package com.swirlds.logging.test.fixtures.internal;

import com.swirlds.base.test.fixtures.util.TestInjector;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

public class LoggerMirrorExtension implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        try (final LoggingMirrorImpl loggingMirror = new LoggingMirrorImpl()) {
            TestInjector.injectInTest(LoggingMirror.class, () -> loggingMirror, extensionContext);
            TestInjector.injectInTest(LoggingMirrorImpl.class, () -> loggingMirror, extensionContext);
            invocation.proceed();
        }
    }
}
