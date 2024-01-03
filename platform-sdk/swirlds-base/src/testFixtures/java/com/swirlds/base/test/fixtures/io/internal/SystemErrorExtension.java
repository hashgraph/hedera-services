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

package com.swirlds.base.test.fixtures.io.internal;

import com.swirlds.base.test.fixtures.io.SystemErrProvider;
import com.swirlds.base.test.fixtures.util.TestInjector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Objects;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * This class is used to inject a {@link SystemErrProvider} instance into a test and run the test in isolation.
 *
 * @see com.swirlds.base.test.fixtures.io.WithSystemError
 */
public class SystemErrorExtension implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(
            @NonNull final Invocation<Void> invocation,
            @NonNull final ReflectiveInvocationContext<Method> invocationContext,
            @NonNull final ExtensionContext extensionContext)
            throws Throwable {
        Objects.requireNonNull(invocation, "invocation must not be null");
        Objects.requireNonNull(extensionContext, "extensionContext must not be null");
        final PrintStream originalSystemErrorPrintStream = System.err;
        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            final SystemIoProvider provider = new SystemIoProvider(byteArrayOutputStream);
            System.setErr(new PrintStream(byteArrayOutputStream));
            TestInjector.injectInTest(SystemErrProvider.class, () -> provider, extensionContext);
            invocation.proceed();
        } finally {
            System.setErr(originalSystemErrorPrintStream);
        }
    }
}
