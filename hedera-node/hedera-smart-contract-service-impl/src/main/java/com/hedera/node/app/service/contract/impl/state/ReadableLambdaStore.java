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

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_KEY;
import static com.hedera.node.app.service.contract.impl.schemas.V061ContractSchema.LAMBDA_STATES_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.LambdaID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.lambda.LambdaState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to lambda states.
 */
public class ReadableLambdaStore {
    private final ReadableKVState<SlotKey, SlotValue> storage;
    private final ReadableKVState<LambdaID, LambdaState> lambdaStates;

    public ReadableLambdaStore(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.storage = states.get(STORAGE_KEY);
        this.lambdaStates = states.get(LAMBDA_STATES_KEY);
    }

    public record LambdaView(@NonNull LambdaState state, @NonNull List<Slot> selectedSlots) {}

    public record Slot(@NonNull SlotKey key, @Nullable SlotValue value) {}

    /**
     * Returns a list of slot values for the given lambda and keys.
     * @param lambdaId the lambda ID
     * @param keys the keys
     * @return a list of slots
     * @throws IllegalArgumentException if the lambda ID is not found
     */
    public LambdaView getView(@NonNull final LambdaID lambdaId, @NonNull final List<Bytes> keys) {
        requireNonNull(lambdaId);
        requireNonNull(keys);
        final var state = lambdaStates.get(lambdaId);
        if (state == null) {
            throw new IllegalArgumentException();
        }
        final var contractId = state.contractIdOrThrow();
        final List<Slot> slots = new ArrayList<>(keys.size());
        keys.forEach(key -> {
            final var slotKey = new SlotKey(contractId, zeroPaddedTo32(key));
            final var slotValue = storage.get(slotKey);
            slots.add(new Slot(slotKey, slotValue));
        });
        return new LambdaView(state, slots);
    }

    protected Bytes zeroPaddedTo32(@NonNull final Bytes bytes) {
        final int len = (int) bytes.length();
        if (len == 32L) {
            return bytes;
        } else {
            final var padded = new byte[32];
            System.arraycopy(bytes.toByteArray(), 0, padded, 32 - len, len);
            return Bytes.wrap(padded);
        }
    }
}
