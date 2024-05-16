/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera;

import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An extension that binds the target network to the thread before invoking
 * each {@link HederaTest}-annotated test method.
 *
 * <p><b>TODO</b> - implement {@link org.junit.jupiter.api.extension.BeforeAllCallback}
 * and {@link org.junit.jupiter.api.extension.AfterAllCallback} to handle
 * creating {@link @Isolated} networks for annotated test classes and targeting
 * them for the duration of the test class.
 */
public class NetworkTargetingExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback {
    @Override
    public void beforeEach(@NonNull final ExtensionContext extensionContext) {
        extensionContext
                .getTestMethod()
                .filter(NetworkTargetingExtension::isHederaTest)
                .ifPresent(ignore -> HapiSpec.TARGET_NETWORK.set(HederaNetwork.SHARED_NETWORK.get()));
    }

    @Override
    public void afterEach(@NonNull final ExtensionContext extensionContext) {
        HapiSpec.TARGET_NETWORK.remove();
    }

    @Override
    public void beforeAll(@NonNull final ExtensionContext extensionContext) throws Exception {}

    private static boolean isHederaTest(@NonNull final Method method) {
        return isAnnotated(method, HederaTest.class) || isAnnotated(method, LeakyHederaTest.class);
    }
}
