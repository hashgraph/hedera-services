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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ABORT;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ONLY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import com.swirlds.common.system.DualState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Simple facility that notifies interested parties when the freeze state is updated.
 */
public class DualStateUpdateFacility {
    private final DualState dualState;

    /**
     * Creates a new instance of this class.
     *
     * @param dualState the configuration provider
     */
    public DualStateUpdateFacility(@NonNull final DualState dualState) {
        this.dualState = requireNonNull(dualState, "configProvider must not be null");
    }

    /**
     * Checks whether the given transaction body is a freeze transaction and eventually
     * notifies the registered facility.
     *
     * @param state the current state
     * @param txBody the transaction body
     */
    public void handleTxBody(@NonNull final HederaState state, @NonNull final TransactionBody txBody) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");

        if (txBody.hasFreeze()) {
            final FreezeType freezeType = txBody.freezeOrThrow().freezeType();
            if (freezeType == FREEZE_UPGRADE || freezeType == FREEZE_ONLY) {
                // copy freeze state to dual state
                final ReadableStates states = state.createReadableStates(FreezeService.NAME);
                final ReadableSingletonState<Timestamp> freezeTime =
                        states.getSingleton(FreezeServiceImpl.FREEZE_TIME_KEY);
                final Instant freezeTimeInstant = Instant.ofEpochSecond(
                        freezeTime.get().seconds(), freezeTime.get().nanos());
                dualState.setFreezeTime(freezeTimeInstant);
            } else if (freezeType == FREEZE_ABORT) {
                // copy freeze state (which is null) to dual state
                // we just set dual state to null
                dualState.setFreezeTime(null);
            } else {
                // for other freeze types, do nothing
            }
        }
    }
}
