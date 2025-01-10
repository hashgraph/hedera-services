/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.roster;

import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.roster.schemas.V0590RosterSchema;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link com.hedera.hapi.node.state.roster.Roster} implementation of the {@link Service} interface.
 * Registers the roster schemas with the {@link SchemaRegistry}.
 * Not exposed outside `hedera-app`.
 */
public class RosterService implements Service {
    /**
     * During migration to the roster lifecycle, the platform state service may need
     * to set its legacy address books based on the current roster. To do this, we
     * need to ensure the roster service is migrated before the platform state service.
     */
    public static final int MIGRATION_ORDER = PLATFORM_STATE_SERVICE.migrationOrder() - 1;

    public static final String NAME = "RosterService";

    /**
     * Temporary access to the disk address book used in upgrade or network transplant
     * scenarios before the roster lifecycle is enabled.
     */
    @Deprecated
    private static final AtomicReference<AddressBook> DISK_ADDRESS_BOOK = new AtomicReference<>();

    /**
     * The test to use to determine if a candidate roster may be
     * adopted at an upgrade boundary.
     */
    private final Predicate<Roster> canAdopt;
    /**
     * Required until the upgrade that adopts the roster lifecycle; at that upgrade boundary,
     * we must initialize the active roster from the platform state's legacy address books.
     */
    @Deprecated
    private final Supplier<State> stateSupplier;

    public RosterService(@NonNull final Predicate<Roster> canAdopt, @NonNull final Supplier<State> stateSupplier) {
        this.canAdopt = requireNonNull(canAdopt);
        this.stateSupplier = requireNonNull(stateSupplier);
    }

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public int migrationOrder() {
        return MIGRATION_ORDER;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(
                new V0590RosterSchema(canAdopt, WritableRosterStore::new, stateSupplier, DISK_ADDRESS_BOOK::get));
    }

    /**
     * Sets the disk address book to the given address book.
     */
    public static void setDiskAddressBook(@NonNull final AddressBook addressBook) {
        DISK_ADDRESS_BOOK.set(requireNonNull(addressBook));
    }

    /**
     * Clears the disk address book.
     */
    public static void clearDiskAddressBook() {
        DISK_ADDRESS_BOOK.set(null);
    }
}
