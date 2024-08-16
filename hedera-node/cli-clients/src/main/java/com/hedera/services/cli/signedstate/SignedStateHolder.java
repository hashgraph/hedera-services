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

package com.hedera.services.cli.signedstate;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.LegacyFileConfigSource;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileReader;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import com.swirlds.platform.system.StaticSoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Navigates a signed state "file" and returns information from it
 *
 * <p>A "signed state" is actually a directory tree, at the top level of which is the serialized
 * merkle tree of the hashgraph state. That is in a file named `SignedState.swh` and file is called
 * the "signed state file". But the whole directory tree must be present.
 *
 * <p>This uses a `SignedStateFileReader` to suck that entire merkle tree into memory, plus the
 * indexes of the virtual maps ("vmap"s) - ~1Gb serialized (2022-11). Then you can traverse the
 * rematerialized hashgraph state.
 *
 * <p>(FUTURE) Needs to be reworked for {@link MerkleStateRoot}.
 */
@SuppressWarnings("java:S5738") // deprecated classes (several current platform classes have no replacement yet)
public class SignedStateHolder implements AutoCloseableNonThrowing {
    @NonNull
    private final Path swhPath;

    @NonNull
    private final ReservedSignedState reservedSignedState;

    public SignedStateHolder(@NonNull final Path swhPath, @NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(swhPath, "swhPath");
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        this.swhPath = swhPath;
        final var state = dehydrate(configurationPaths);
        reservedSignedState = state.getLeft();
    }

    @Override
    public void close() {
        reservedSignedState.close();
    }

    public enum Validity {
        ACTIVE,
        DELETED
    }

    /**
     * A contract - some bytecode associated with its contract id(s)
     *
     * @param ids - direct from the signed state file there's one contract id for each bytecode, but
     *     there are duplicates which can be coalesced and then there's a set of ids for the single
     *     contract; kept in sorted order by the container `TreeSet` so it's easy to get the canonical
     *     id for the contract, and also you can't forget to process them in a deterministic order
     * @param bytecode - bytecode of the contract
     * @param validity - whether the contract is valid or note, aka active or deleted
     */
    public record Contract(
            @NonNull TreeSet</*@NonNull*/ Integer> ids, @NonNull byte[] bytecode, @NonNull Validity validity) {

        // For any set of contract ids with the same bytecode, the lowest contract id is used as the "canonical"
        // id for that bytecode (useful for ordering contracts deterministically)
        public int canonicalId() {
            return ids.first();
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (o == this) return true;
            return o instanceof Contract other
                    && new EqualsBuilder()
                            .append(ids, other.ids)
                            .append(bytecode, other.bytecode)
                            .append(validity, other.validity)
                            .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(ids)
                    .append(bytecode)
                    .append(validity)
                    .toHashCode();
        }

        @Override
        public String toString() {
            var csvIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));
            return "Contract{ids=(%s), %s, bytecode=%s}".formatted(csvIds, validity, Arrays.toString(bytecode));
        }
    }

    /**
     * All contracts extracted from a signed state file
     *
     * @param contracts - dictionary of contract bytecodes indexed by their contract id (as a Long)
     * @param deletedContracts - collection of ids of deleted contracts
     * @param registeredContractsCount - total #contracts known to the _accounts_ in the signed
     *     state file (not all actually have bytecodes in the file store, and of those, some have
     *     0-length bytecode files)
     */
    public record Contracts(
            @NonNull Collection</*@NonNull*/ Contract> contracts,
            @NonNull Collection<Integer> deletedContracts,
            int registeredContractsCount) {}

    /** Deserialize the signed state file into an in-memory data structure. */
    @NonNull
    private Pair<ReservedSignedState, MerkleStateRoot> dehydrate(@NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        registerConstructables();

        final var platformContext = PlatformContext.create(buildConfiguration(configurationPaths));

        ReservedSignedState rss;
        try {
            rss = SignedStateFileReader.readStateFile(platformContext, swhPath, SignedStateFileUtils::readState)
                    .reservedSignedState();
            StaticSoftwareVersion.setSoftwareVersion(
                    rss.get().getState().getPlatformState().getCreationSoftwareVersion());
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (null == rss) throw new MissingSignedStateComponent("ReservedSignedState", swhPath);

        final var swirldsState = rss.get().getSwirldState();
        if (!(swirldsState
                instanceof
                MerkleStateRoot
                merkleStateRoot)) { // Java booboo: precedence level of `instanceof` is way too low
            rss.close();
            throw new MissingSignedStateComponent("MerkleStateRoot", swhPath);
        }

        return Pair.of(rss, merkleStateRoot);
    }

    /** Build a configuration object from the provided configuration paths. */
    private Configuration buildConfiguration(@NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        final var builder = ConfigurationBuilder.create().autoDiscoverExtensions();

        for (@NonNull final var path : configurationPaths) {
            Objects.requireNonNull(path, "path");
            try {
                builder.withSource(new LegacyFileConfigSource(path));
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        final var configuration = builder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);
        return configuration;
    }

    /** register all applicable classes on classpath before deserializing signed state */
    private void registerConstructables() {
        try {
            final var registry = ConstructableRegistry.getInstance();
            registry.registerConstructables("com.swirlds.*");
        } catch (final ConstructableRegistryException ex) {
            throw new UncheckedConstructableRegistryException(ex);
        }
    }

    public static class UncheckedConstructableRegistryException extends RuntimeException {
        public UncheckedConstructableRegistryException(@NonNull final ConstructableRegistryException ex) {
            super(ex);
        }
    }

    public static class MissingSignedStateComponent extends NullPointerException {
        public MissingSignedStateComponent(@NonNull final String component, @NonNull final Path swhPath) {
            super("Expected non-null %s from signed state file %s".formatted(component, swhPath.toString()));
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(swhPath, "swhPath");
        }
    }

    private void assertSignedStateComponentExists(
            @Nullable final Object component, @NonNull final String componentName) {
        Objects.requireNonNull(componentName, "componentName");

        if (null == component) throw new MissingSignedStateComponent(componentName, swhPath);
    }
}
