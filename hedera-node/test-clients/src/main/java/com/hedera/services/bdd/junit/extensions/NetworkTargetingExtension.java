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

package com.hedera.services.bdd.junit.extensions;

import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.extensions.ExtensionUtils.hapiTestMethodOf;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyEmbeddedHapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * An extension that binds the target network to the current thread before invoking
 * each {@link HapiTest}-annotated test method.
 *
 * <p><b>(FUTURE)</b> - implement {@link org.junit.jupiter.api.extension.BeforeAllCallback}
 * and {@link org.junit.jupiter.api.extension.AfterAllCallback} to handle creating "private"
 * networks for annotated test classes and targeting them instead of the shared network.
 */
public class NetworkTargetingExtension implements BeforeEachCallback, AfterEachCallback {
    public static final AtomicReference<HederaNetwork> SHARED_NETWORK = new AtomicReference<>();
    public static final AtomicReference<RepeatableKeyGenerator> REPEATABLE_KEY_GENERATOR = new AtomicReference<>();

    @Override
    public void beforeEach(@NonNull final ExtensionContext extensionContext) {
        hapiTestMethodOf(extensionContext).ifPresent(method -> {
            if (isAnnotated(method, GenesisHapiTest.class)) {
                final var targetNetwork = new EmbeddedNetwork(method.getName().toUpperCase(), method.getName());
                targetNetwork.start();
                HapiSpec.TARGET_NETWORK.set(targetNetwork);
            } else {
                HapiSpec.TARGET_NETWORK.set(SHARED_NETWORK.get());
                // If there are properties to preserve or system files to override and restore, bind that info to the
                // thread before executing the test factory
                if (isAnnotated(method, LeakyHapiTest.class)) {
                    HapiSpec.PROPERTIES_TO_PRESERVE.set(
                            List.of(method.getAnnotation(LeakyHapiTest.class).overrides()));
                    HapiSpec.THROTTLES_OVERRIDE.set(effectiveThrottleResource(
                            method.getAnnotation(LeakyHapiTest.class).requirement(),
                            method.getAnnotation(LeakyHapiTest.class).throttles()));
                } else if (isAnnotated(method, LeakyEmbeddedHapiTest.class)) {
                    HapiSpec.PROPERTIES_TO_PRESERVE.set(List.of(
                            method.getAnnotation(LeakyEmbeddedHapiTest.class).overrides()));
                    HapiSpec.THROTTLES_OVERRIDE.set(effectiveThrottleResource(
                            method.getAnnotation(LeakyEmbeddedHapiTest.class).requirement(),
                            method.getAnnotation(LeakyEmbeddedHapiTest.class).throttles()));
                }
            }
        });
    }

    @Override
    public void afterEach(@NonNull final ExtensionContext extensionContext) {
        HapiSpec.TARGET_NETWORK.remove();
        HapiSpec.THROTTLES_OVERRIDE.remove();
        HapiSpec.PROPERTIES_TO_PRESERVE.remove();
    }

    /**
     * If there is an explicit throttle resource to load, returns it; otherwise returns null if the test's
     * context requirement does not include overriding throttles.
     * @param contextRequirements the context requirements of the test
     * @param throttles the path to the throttle resource
     * @return the effective throttle resource
     */
    private @Nullable String effectiveThrottleResource(
            @NonNull final ContextRequirement[] contextRequirements, @NonNull final String throttles) {
        if (!throttles.isBlank()) {
            return throttles;
        }
        return List.of(contextRequirements).contains(THROTTLE_OVERRIDES) ? "" : null;
    }
}
