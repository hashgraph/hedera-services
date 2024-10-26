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

import static com.hedera.services.bdd.junit.ContextRequirement.FEE_SCHEDULE_OVERRIDES;
import static com.hedera.services.bdd.junit.ContextRequirement.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener.SharedNetworkExecutionListener.*;
import static com.hedera.services.bdd.junit.extensions.ExtensionUtils.hapiTestMethodOf;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.CONCURRENT;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyEmbeddedHapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
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
                final var targetNetwork =
                        new EmbeddedNetwork(method.getName().toUpperCase(), method.getName(), CONCURRENT);
                targetNetwork.start();
                HapiSpec.TARGET_NETWORK.set(targetNetwork);
            } else {
                requiredEmbeddedMode(extensionContext)
                        .ifPresent(
                                SharedNetworkLauncherSessionListener.SharedNetworkExecutionListener::ensureEmbedding);
                HapiSpec.TARGET_NETWORK.set(SHARED_NETWORK.get());
                // If there are properties to preserve or system files to override and restore, bind that info to the
                // thread before executing the test factory
                if (isAnnotated(method, LeakyHapiTest.class)) {
                    final var a = method.getAnnotation(LeakyHapiTest.class);
                    bindThreadTargets(a.requirement(), a.overrides(), a.throttles(), a.fees());
                } else if (isAnnotated(method, LeakyEmbeddedHapiTest.class)) {
                    final var a = method.getAnnotation(LeakyEmbeddedHapiTest.class);
                    bindThreadTargets(a.requirement(), a.overrides(), a.throttles(), a.fees());
                } else if (isAnnotated(method, LeakyRepeatableHapiTest.class)) {
                    final var a = method.getAnnotation(LeakyRepeatableHapiTest.class);
                    bindThreadTargets(new ContextRequirement[] {}, a.overrides(), a.throttles(), a.fees());
                }
            }
        });
    }

    @Override
    public void afterEach(@NonNull final ExtensionContext extensionContext) {
        HapiSpec.TARGET_NETWORK.remove();
        HapiSpec.FEES_OVERRIDE.remove();
        HapiSpec.THROTTLES_OVERRIDE.remove();
        HapiSpec.PROPERTIES_TO_PRESERVE.remove();
    }

    private Optional<EmbeddedMode> requiredEmbeddedMode(@NonNull final ExtensionContext extensionContext) {
        return extensionContext
                .getTestClass()
                .map(type -> type.getAnnotation(TargetEmbeddedMode.class))
                .map(TargetEmbeddedMode::value)
                .or(() -> extensionContext.getParent().flatMap(this::requiredEmbeddedMode));
    }

    private void bindThreadTargets(
            @NonNull final ContextRequirement[] requirement,
            @NonNull final String[] overrides,
            @NonNull final String throttles,
            @NonNull final String fees) {
        HapiSpec.PROPERTIES_TO_PRESERVE.set(List.of(overrides));
        HapiSpec.THROTTLES_OVERRIDE.set(effectiveResource(requirement, THROTTLE_OVERRIDES, throttles));
        HapiSpec.FEES_OVERRIDE.set(effectiveResource(requirement, FEE_SCHEDULE_OVERRIDES, fees));
    }

    /**
     * If there is an explicit resource to load, returns it; otherwise returns null if the test's
     * context requirement does not include the relevant requirement.
     * @param contextRequirements the context requirements of the test
     * @param relevantRequirement the relevant context requirement
     * @param resource the path to the resource
     * @return the effective resource
     */
    private @Nullable String effectiveResource(
            @NonNull final ContextRequirement[] contextRequirements,
            @NonNull final ContextRequirement relevantRequirement,
            @NonNull final String resource) {
        if (!resource.isBlank()) {
            return resource;
        }
        return List.of(contextRequirements).contains(relevantRequirement) ? "" : null;
    }
}
