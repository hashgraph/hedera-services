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

package com.swirlds.platform.state.service.schemas;

import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static com.swirlds.state.lifecycle.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A restart-only schema to ensure the platform state has its active and previous
 * address books configured correctly when the roster lifecycle is disabled.
 * <p>
 * <b>(FUTURE)</b> Delete at the same time as the {@link AddressBookConfig#useRosterLifecycle()}
 * feature flag.
 */
@Deprecated
public class V058RosterLifecycleTransitionSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(58).build();

    private final Supplier<AddressBook> addressBook;
    private final Function<Configuration, SoftwareVersion> appVersionFn;
    private final Function<WritableStates, WritablePlatformStateStore> platformStateStoreFn;

    public V058RosterLifecycleTransitionSchema(
            @NonNull final Supplier<AddressBook> addressBook,
            @NonNull final Function<Configuration, SoftwareVersion> appVersionFn,
            @NonNull final Function<WritableStates, WritablePlatformStateStore> platformStateStoreFn) {
        super(VERSION);
        this.addressBook = requireNonNull(addressBook);
        this.appVersionFn = requireNonNull(appVersionFn);
        this.platformStateStoreFn = requireNonNull(platformStateStoreFn);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            final var stateSingleton = ctx.newStates().<PlatformState>getSingleton(PLATFORM_STATE_KEY);
            final var state = requireNonNull(stateSingleton.get());
            // Null out the legacy address book fields
            stateSingleton.put(state.copyBuilder()
                    .previousAddressBook((com.hedera.hapi.platform.state.AddressBook) null)
                    .addressBook(((com.hedera.hapi.platform.state.AddressBook) null))
                    .build());
        }
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final var addressBookConfig = ctx.appConfig().getConfigData(AddressBookConfig.class);
        if (!addressBookConfig.useRosterLifecycle() && !ctx.isGenesis()) {
            final boolean addressBookChanged = ctx.isUpgrade(
                            config -> new Semver(appVersionFn.apply(config).getPbjSemanticVersion()), Semver::new)
                    || addressBookConfig.forceUseOfConfigAddressBook();
            if (addressBookChanged) {
                final var stateStore = platformStateStoreFn.apply(ctx.newStates());
                final var currentBook = stateStore.getAddressBook();
                stateStore.bulkUpdate(v -> {
                    v.setPreviousAddressBook(currentBook == null ? null : currentBook.copy());
                    v.setAddressBook(requireNonNull(addressBook.get()).copy());
                });
            }
        }
    }

    /**
     * A comparable wrapper around a {@link SemanticVersion} to allow for version comparison.
     * @param version the version to wrap
     */
    private record Semver(@NonNull SemanticVersion version) implements Comparable<Semver> {
        @Override
        public int compareTo(@NonNull final Semver that) {
            return SEMANTIC_VERSION_COMPARATOR.compare(this.version, that.version);
        }
    }
}
