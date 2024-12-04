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
import static com.hedera.services.bdd.junit.extensions.ExtensionUtils.hapiTestMethodOf;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.CONCURRENT;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_ENCRYPTION_KEYS;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_KEY_MATERIAL_GENERATOR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.workingDirVersion;
import static com.hedera.services.bdd.junit.restart.StartupAssets.ROSTER_AND_ENCRYPTION_KEYS;
import static com.hedera.services.bdd.junit.restart.StartupAssets.ROSTER_AND_FULL_TSS_KEY_MATERIAL;
import static com.hedera.services.bdd.spec.HapiSpec.doTargetSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.ConfigOverride;
import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyEmbeddedHapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.TssKeyMaterial;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.junit.restart.RestartHapiTest;
import com.hedera.services.bdd.junit.restart.SavedStateSpec;
import com.hedera.services.bdd.junit.restart.StartupAssets;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.RepeatableKeyGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongFunction;
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
    private static final String SPEC_NAME = "<RESTART>";
    private static final Set<StartupAssets> OVERRIDES_WITH_ENCRYPTION_KEYS =
            EnumSet.of(ROSTER_AND_ENCRYPTION_KEYS, ROSTER_AND_FULL_TSS_KEY_MATERIAL);

    public static final AtomicReference<HederaNetwork> SHARED_NETWORK = new AtomicReference<>();
    public static final AtomicReference<RepeatableKeyGenerator> REPEATABLE_KEY_GENERATOR = new AtomicReference<>();

    @Override
    public void beforeEach(@NonNull final ExtensionContext extensionContext) {
        hapiTestMethodOf(extensionContext).ifPresent(method -> {
            if (isAnnotated(method, GenesisHapiTest.class)) {
                final var targetNetwork =
                        new EmbeddedNetwork(method.getName().toUpperCase(), method.getName(), CONCURRENT);
                final var a = method.getAnnotation(GenesisHapiTest.class);
                final var bootstrapOverrides = Arrays.stream(a.bootstrapOverrides())
                        .collect(toMap(ConfigOverride::key, ConfigOverride::value));
                targetNetwork.startWith(bootstrapOverrides, nodeId -> Bytes.EMPTY, nodes -> Optional.empty());
                HapiSpec.TARGET_NETWORK.set(targetNetwork);
            } else if (isAnnotated(method, RestartHapiTest.class)) {
                final var targetNetwork =
                        new EmbeddedNetwork(method.getName().toUpperCase(), method.getName(), REPEATABLE);
                final var a = method.getAnnotation(RestartHapiTest.class);
                final var overrides = Arrays.stream(a.bootstrapOverrides())
                        .collect(toMap(ConfigOverride::key, ConfigOverride::value));
                final LongFunction<Bytes> tssEncryptionKeyFn =
                        OVERRIDES_WITH_ENCRYPTION_KEYS.contains(a.startupAssets())
                                ? CLASSIC_ENCRYPTION_KEYS::get
                                : nodeId -> Bytes.EMPTY;
                final Function<List<RosterEntry>, Optional<TssKeyMaterial>> tssKeyMaterialFn = a.startupAssets()
                                == ROSTER_AND_FULL_TSS_KEY_MATERIAL
                        ? rosterEntries -> Optional.of(CLASSIC_KEY_MATERIAL_GENERATOR.apply(new Roster(rosterEntries)))
                        : rosterEntries -> Optional.empty();
                switch (a.restartType()) {
                    case GENESIS, UPGRADE_BOUNDARY -> targetNetwork.startWith(
                            overrides, tssEncryptionKeyFn, tssKeyMaterialFn);
                    case SAME_VERSION -> startFromPreviousVersion(targetNetwork, overrides);
                }
                switch (a.restartType()) {
                    case GENESIS -> {
                        // The restart was from genesis, so nothing else to do
                    }
                    case SAME_VERSION, UPGRADE_BOUNDARY -> {
                        final var state = postGenesisStateOf(targetNetwork, a);
                        targetNetwork.restart(state, overrides);
                    }
                }
                HapiSpec.TARGET_NETWORK.set(targetNetwork);
            } else {
                ensureEmbeddedNetwork(extensionContext);
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

    /**
     * Ensures that the embedded network is running, if required by the test class or method.
     *
     * @param extensionContext the extension context
     */
    public static void ensureEmbeddedNetwork(@NonNull final ExtensionContext extensionContext) {
        requireNonNull(extensionContext);
        requiredEmbeddedMode(extensionContext)
                .ifPresent(SharedNetworkLauncherSessionListener.SharedNetworkExecutionListener::ensureEmbedding);
    }

    /**
     * Returns the embedded mode required by the test class or method, if any.
     *
     * @param extensionContext the extension context
     * @return the embedded mode
     */
    private static Optional<EmbeddedMode> requiredEmbeddedMode(@NonNull final ExtensionContext extensionContext) {
        requireNonNull(extensionContext);
        return extensionContext
                .getTestClass()
                .map(type -> type.getAnnotation(TargetEmbeddedMode.class))
                .map(TargetEmbeddedMode::value)
                .or(() -> extensionContext.getParent().flatMap(NetworkTargetingExtension::requiredEmbeddedMode));
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
     *
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

    /**
     * Starts the given target embedded network from the previous version with any other requested overrides.
     *
     * @param targetNetwork the target network
     * @param bootstrapOverrides the overrides
     */
    private void startFromPreviousVersion(
            @NonNull final EmbeddedNetwork targetNetwork, @NonNull final Map<String, String> bootstrapOverrides) {
        final Map<String, String> overrides = new HashMap<>(bootstrapOverrides);
        final var currentVersion = workingDirVersion();
        final var previousVersion = currentVersion
                .copyBuilder()
                .minor(currentVersion.minor() - 1)
                .patch(0)
                .pre("")
                .build("")
                .build();
        overrides.put("hedera.services.version", HapiUtils.toString(previousVersion));
        targetNetwork.startWith(overrides, nodeId -> Bytes.EMPTY, nodes -> Optional.empty());
    }

    private FakeState postGenesisStateOf(
            @NonNull final EmbeddedNetwork targetNetwork, @NonNull final RestartHapiTest a) {
        final var spec = new HapiSpec(SPEC_NAME, new SpecOperation[] {cryptoCreate("genesisAccount")});
        doTargetSpec(spec, targetNetwork);
        try {
            spec.execute();
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        final SavedStateSpec savedStateSpec;
        try {
            savedStateSpec = a.savedStateSpec().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        final var state = targetNetwork.embeddedHederaOrThrow().state();
        savedStateSpec.accept(state);
        return state;
    }
}
