/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_LAMBDA_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_LAMBDA_INDEX;
import static com.hedera.hapi.node.base.ResponseCodeEnum.LAMBDA_INDEX_IN_USE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.LAMBDA_INSTALLATION_MISSING_INIT_METHOD;
import static com.hedera.node.app.hapi.utils.EntityType.LAMBDA;
import static com.hedera.node.app.service.contract.impl.infra.IterableStorageManager.insertAccessedValue;
import static com.hedera.node.app.service.contract.impl.infra.IterableStorageManager.removeAccessedValue;
import static com.hedera.node.app.service.contract.impl.schemas.V061ContractSchema.LAMBDA_STATES_KEY;
import static com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType.INSERTION;
import static com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType.REMOVAL;
import static com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType.UPDATE;
import static com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType.ZERO_INTO_EMPTY_SLOT;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.LambdaID;
import com.hedera.hapi.node.lambda.LambdaInstallation;
import com.hedera.hapi.node.lambda.LambdaStorageSlot;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.lambda.LambdaState;
import com.hedera.node.app.service.contract.impl.state.StorageAccess.StorageAccessType;
import com.hedera.node.app.spi.ids.WritableEntityCounters;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Read/write access to lambda states.
 */
public class WritableLambdaStore extends ReadableLambdaStore {
    private final ContractStateStore stateStore;
    private final WritableEntityCounters entityCounters;
    private final WritableKVState<LambdaID, LambdaState> lambdaStates;

    public WritableLambdaStore(
            @NonNull final WritableStates states, @NonNull final WritableEntityCounters entityCounters) {
        super(states);
        this.stateStore = new WritableContractStateStore(states, entityCounters);
        this.entityCounters = requireNonNull(entityCounters);
        this.lambdaStates = states.get(LAMBDA_STATES_KEY);
    }

    /**
     * Puts the given slot values for the given lambda, ensuring storage linked list pointers are preserved.
     * If a new value is {@link Bytes#EMPTY}, the slot is removed.
     *
     * @param lambdaId the lambda ID
     * @param slots the slot updates
     * @throws HandleException if the lambda ID is not found
     */
    public void updateSlots(@NonNull final LambdaID lambdaId, @NonNull final List<LambdaStorageSlot> slots)
            throws HandleException {
        final List<Bytes> keys = new ArrayList<>(slots.size());
        for (final var slot : slots) {
            keys.add(slot.key());
        }
        final var view = getView(lambdaId, keys);
        final var contractId = view.contractId();
        var firstKey = view.firstStorageKey();
        int slotUsageChange = 0;
        for (int i = 0, n = keys.size(); i < n; i++) {
            final var slot = view.selectedSlots().get(i);
            final var update = SlotUpdate.from(slot, slots.get(i).value());
            firstKey = switch (update.asAccessType()) {
                case REMOVAL -> {
                    slotUsageChange--;
                    yield removeAccessedValue(stateStore, firstKey, contractId, update.key());
                }
                case INSERTION -> {
                    slotUsageChange++;
                    yield insertAccessedValue(stateStore, firstKey, update.newValueOrThrow(), contractId, update.key());
                }
                case UPDATE -> {
                    final var slotValue =
                            new SlotValue(update.newValueOrThrow(), slot.effectivePrevKey(), slot.effectiveNextKey());
                    stateStore.putSlot(slot.key(), slotValue);
                    yield firstKey;
                }
                default -> firstKey;};
        }
        if (slotUsageChange != 0) {
            final var oldState = view.state();
            lambdaStates.put(
                    lambdaId,
                    oldState.copyBuilder()
                            .firstContractStorageKey(firstKey)
                            .numStorageSlots(oldState.numStorageSlots() + slotUsageChange)
                            .build());
            stateStore.adjustSlotCount(slotUsageChange);
        }
    }

    /**
     * Marks the lambda as deleted.
     *
     * @param lambdaId the lambda ID
     * @throws HandleException if the lambda ID is not found
     */
    public void markDeleted(@NonNull final LambdaID lambdaId) {
        final var state = lambdaStates.get(lambdaId);
        validateTrue(state != null, INVALID_LAMBDA_ID);
        lambdaStates.put(lambdaId, state.copyBuilder().deleted(true).build());
    }

    /**
     * Tries to install a new lambda with the given id.
     *
     * @param lambdaId the lambda ID
     * @param installation the installation
     * @param contractNumSupplier the contract number supplier
     * @param hederaConfig the Hedera configuration
     * @throws HandleException if the installation is invalid
     */
    public void installLambda(
            final long nextLambdaIndex,
            @NonNull final LambdaID lambdaId,
            @NonNull final LongSupplier contractNumSupplier,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final LambdaInstallation installation)
            throws HandleException {
        validateTrue(lambdaStates.get(lambdaId) == null, LAMBDA_INDEX_IN_USE);
        final var builder = LambdaState.newBuilder();
        if (nextLambdaIndex != 0L) {
            final var nextLambdaId =
                    lambdaId.copyBuilder().index(nextLambdaIndex).build();
            var nextLambda = lambdaStates.get(nextLambdaId);
            validateTrue(nextLambda != null, INVALID_LAMBDA_INDEX);
            nextLambda =
                    nextLambda.copyBuilder().previousIndex(lambdaId.index()).build();
            lambdaStates.put(nextLambdaId, nextLambda);
        }
        final var contractId = ContractID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .contractNum(contractNumSupplier.getAsLong())
                .build();
        final var state = builder.contractId(contractId)
                .type(installation.type())
                .chargingPattern(installation.chargingPattern())
                .defaultGasLimit(installation.defaultGasLimit())
                .nextIndex(nextLambdaIndex)
                .build();
        lambdaStates.put(lambdaId, state);
        switch (installation.initMethod().kind()) {
            case UNSET -> throw new HandleException(LAMBDA_INSTALLATION_MISSING_INIT_METHOD);
            case INITCODE -> throw new AssertionError("Not implemented");
            case EXPLICIT_INIT -> {
                final var explicitInit = installation.explicitInitOrThrow();
                final var raw =
                        switch (explicitInit.bytecodeSource().kind()) {
                            case UNSET -> throw new HandleException(LAMBDA_INSTALLATION_MISSING_INIT_METHOD);
                            case BYTECODE_FILE_ID -> throw new AssertionError("Not implemented");
                            case BYTECODE -> explicitInit.bytecodeOrThrow();
                        };
                stateStore.putBytecode(contractId, new Bytecode(raw));
                if (!explicitInit.storageSlots().isEmpty()) {
                    updateSlots(lambdaId, explicitInit.storageSlots());
                }
            }
        }
        entityCounters.incrementEntityTypeCount(LAMBDA);
    }

    private record SlotUpdate(@NonNull Bytes key, @Nullable Bytes oldValue, @Nullable Bytes newValue) {
        public static SlotUpdate from(@NonNull final Slot slot, @NonNull final Bytes value) {
            return new SlotUpdate(slot.key().key(), slot.maybeBytesValue(), Bytes.EMPTY.equals(value) ? null : value);
        }

        public @NonNull Bytes newValueOrThrow() {
            return zeroPaddedTo32(requireNonNull(newValue));
        }

        public StorageAccessType asAccessType() {
            if (oldValue == null) {
                return newValue == null ? ZERO_INTO_EMPTY_SLOT : INSERTION;
            } else {
                return newValue == null ? REMOVAL : UPDATE;
            }
        }
    }
}
