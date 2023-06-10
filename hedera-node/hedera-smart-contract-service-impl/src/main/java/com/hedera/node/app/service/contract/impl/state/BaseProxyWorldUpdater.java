/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.state;

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.STORAGE_KEY;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.infra.LegibleStorageManager;
import com.hedera.node.app.service.contract.impl.infra.RentCalculator;
import com.hedera.node.app.service.contract.impl.infra.StorageSizeValidator;
import com.hedera.node.app.spi.meta.bni.Scope;
import com.hedera.node.app.spi.state.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * A {@link ProxyWorldUpdater} that enforces several Hedera-specific checks and actions before
 * making the final commit in the "base" {@link Scope}. These include validating storage size
 * limits, calculating and charging rent, and preserving per-contract linked lists. See the
 * {@link #commit()} implementation for more details.
 */
@TransactionScope
public class BaseProxyWorldUpdater extends ProxyWorldUpdater {
    private final RentCalculator rentCalculator;
    private final LegibleStorageManager storageManager;
    private final StorageSizeValidator storageSizeValidator;

    @Inject
    public BaseProxyWorldUpdater(
            @NonNull final Scope scope,
            @NonNull final EvmFrameStateFactory evmFrameStateFactory,
            @NonNull final RentCalculator rentCalculator,
            @NonNull final LegibleStorageManager storageManager,
            @NonNull final StorageSizeValidator storageSizeValidator) {
        super(scope, evmFrameStateFactory, null);
        this.storageManager = storageManager;
        this.rentCalculator = rentCalculator;
        this.storageSizeValidator = storageSizeValidator;
    }

    /**
     * Before committing the changes to the base scope via {@code super.commit()}, does the following steps:
     * <ol>
     *     <li>Gets the list of pending storage changes and summarizes their effects on size.</li>
     *     <li>Validates the effects on size are legal.</li>
     *     <li>For each increase in storage size, calculates rent and tries to charge the allocating contract.</li>
     *     <li>"Rewrites" the pending storage changes to preserve per-contract linked lists.</li>
     * </ol>
     */
    @Override
    public void commit() {
        // Get the pending changes and summarize their effects on size
        final var changes = evmFrameState.getPendingStorageChanges();
        final var sizeEffects = summarizeSizeEffects(changes);

        // Validate the effects on size are legal
        storageSizeValidator.assertValid(sizeEffects.finalSlotsUsed(), scope, sizeEffects.sizeChanges());

        // For each increase in storage size, calculate rent and try to charge the allocating contract
        chargeRentFor(sizeEffects);

        // "Rewrite" the pending storage changes to preserve per-contract linked lists
        final WritableKVState<SlotKey, SlotValue> storage =
                scope.writableContractState().get(STORAGE_KEY);
        storageManager.rewrite(scope, changes, sizeEffects.sizeChanges(), storage);

        super.commit();
    }

    private record SizeEffects(long finalSlotsUsed, List<StorageSizeChange> sizeChanges) {}

    private SizeEffects summarizeSizeEffects(@NonNull final List<StorageChanges> allChanges) {
        // The initial K/V state will still include the slots being "zeroed out"; i.e., removed
        var finalSlotsUsed = evmFrameState.getKvStateSize();
        final List<StorageSizeChange> sizeChanges = new ArrayList<>();
        for (final var changes : allChanges) {
            final var sizeChange = changes.summarizeSizeEffects();
            sizeChanges.add(sizeChange);
            finalSlotsUsed -= sizeChange.numRemovals();
        }
        return new SizeEffects(finalSlotsUsed, sizeChanges);
    }

    private void chargeRentFor(@NonNull final SizeEffects sizeEffects) {
        for (final var sizeChange : sizeEffects.sizeChanges()) {
            if (sizeChange.numAdded() > 0) {
                final var rentFactors = evmFrameState.getRentFactorsFor(sizeChange.contractNumber());
                // Calculate rent and try to charge the allocating contract
                final var rentInTinycents = rentCalculator.computeFor(
                        sizeEffects.finalSlotsUsed(),
                        sizeChange.numAdded(),
                        rentFactors.numSlotsUsed(),
                        rentFactors.expiry());
                final var rentInTinybars = scope.fees().costInTinybars(rentInTinycents);
                scope.dispatch().chargeStorageRent(sizeChange.contractNumber(), rentInTinybars, true);
            }
        }
    }
}
