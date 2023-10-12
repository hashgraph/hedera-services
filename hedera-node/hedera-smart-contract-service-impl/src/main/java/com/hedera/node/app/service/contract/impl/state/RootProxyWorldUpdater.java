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

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.infra.IterableStorageManager;
import com.hedera.node.app.service.contract.impl.infra.RentCalculator;
import com.hedera.node.app.service.contract.impl.infra.StorageSizeValidator;
import com.hedera.node.app.spi.workflows.ResourceExhaustedException;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/**
 * A {@link ProxyWorldUpdater} that enforces several Hedera-specific checks and actions before
 * making the final commit in the "base" {@link HandleHederaOperations}. These include validating storage size
 * limits, calculating and charging rent, and preserving per-contract linked lists. See the
 * {@link #commit()} implementation for more details.
 */
@TransactionScope
public class RootProxyWorldUpdater extends ProxyWorldUpdater {
    private final RentCalculator rentCalculator;
    private final ContractsConfig contractsConfig;
    private final IterableStorageManager storageManager;
    private final StorageSizeValidator storageSizeValidator;

    private boolean committed = false;
    private List<ContractID> createdContractIds;
    private List<ContractNonceInfo> updatedContractNonces;

    @Inject
    public RootProxyWorldUpdater(
            @NonNull final Enhancement enhancement,
            @NonNull final ContractsConfig contractsConfig,
            @NonNull final EvmFrameStateFactory evmFrameStateFactory,
            @NonNull final RentCalculator rentCalculator,
            @NonNull final IterableStorageManager storageManager,
            @NonNull final StorageSizeValidator storageSizeValidator) {
        super(enhancement, evmFrameStateFactory, null);
        this.contractsConfig = Objects.requireNonNull(contractsConfig);
        this.storageManager = Objects.requireNonNull(storageManager);
        this.rentCalculator = Objects.requireNonNull(rentCalculator);
        this.storageSizeValidator = Objects.requireNonNull(storageSizeValidator);
    }

    /**
     * Before committing the changes to the base scope via {@code super.commit()}, does the following steps:
     * <ol>
     *     <li>Gets the list of pending storage changes and summarizes their effects on size.</li>
     *     <li>Validates the effects on size are legal.</li>
     *     <li>For each increase in storage size, calculates rent and tries to charge the allocating contract.</li>
     *     <li>"Rewrites" the pending storage changes to preserve per-contract linked lists.</li>
     * </ol>
     *
     * @throws ResourceExhaustedException if the storage size limit is exceeded or rent cannot be paid
     */
    @Override
    public void commit() {
        // Validate the effects on size are legal
        final var changes = evmFrameState.getStorageChanges();
        final var sizeEffects = summarizeSizeEffects(changes);
        storageSizeValidator.assertValid(
                sizeEffects.finalSlotsUsed(), enhancement.operations(), sizeEffects.sizeChanges());
        // Charge rent for each increase in storage size
        chargeRentFor(sizeEffects);
        // "Rewrite" the pending storage changes to preserve per-contract linked lists
        storageManager.persistChanges(
                enhancement.operations(),
                changes,
                sizeEffects.sizeChanges(),
                enhancement.operations().getStore());

        // We now have an apparently valid change set, and want to capture some summary
        // information for the Hedera record
        final var contractChangeSummary = enhancement.operations().summarizeContractChanges();
        createdContractIds = contractChangeSummary.newContractIds();
        updatedContractNonces = contractChangeSummary.updatedContractNonces();
        super.commit();
        // Be sure not to externalize contract ids or nonces without a successful commit
        committed = true;
    }

    /**
     * If a successful commit has been made, returns the list of contract ids created during the transaction.
     *
     * @return the list of contract ids created during the transaction
     * @throws IllegalStateException if a commit has not been made successfully
     */
    public @NonNull List<ContractID> getCreatedContractIds() {
        if (!committed) {
            throw new IllegalStateException("No successful commit has been made");
        }
        return createdContractIds;
    }

    /**
     * If a successful commit has been made, returns the map of contract ids to nonces updated during the transaction.
     *
     * @return the list of nonces updated during the transaction
     * @throws IllegalStateException if a commit has not been made successfully
     */
    public List<ContractNonceInfo> getUpdatedContractNonces() {
        if (!committed) {
            throw new IllegalStateException("No successful commit has been made");
        }
        return updatedContractNonces;
    }

    private record SizeEffects(long finalSlotsUsed, List<StorageSizeChange> sizeChanges) {}

    private SizeEffects summarizeSizeEffects(@NonNull final List<StorageAccesses> allChanges) {
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
                final var rentInTinybars = enhancement.operations().valueInTinybars(rentInTinycents);
                enhancement.operations().chargeStorageRent(sizeChange.contractNumber(), rentInTinybars, true);
            }
        }
    }
}
