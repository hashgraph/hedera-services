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
import com.swirlds.platform.state.HederaState;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.spi.ReadableSingletonState;
import com.swirlds.platform.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple facility that notifies interested parties when the freeze state is updated.
 */
@Singleton
public class PlatformStateUpdateFacility {
    private static final Logger logger = LogManager.getLogger(PlatformStateUpdateFacility.class);

    /**
     * Creates a new instance of this class.
     */
    @Inject
    public PlatformStateUpdateFacility() {
        // For dagger
    }

    /**
     * Checks whether the given transaction body is a freeze transaction and eventually
     * notifies the registered facility.
     *
     * @param state the current state
     * @param txBody the transaction body
     */
    public void handleTxBody(
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final TransactionBody txBody) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");

        if (txBody.hasFreeze()) {
            final FreezeType freezeType = txBody.freezeOrThrow().freezeType();
            if (freezeType == FREEZE_UPGRADE || freezeType == FREEZE_ONLY) {
                logger.info("Transaction freeze of type {} detected", freezeType);
                // copy freeze state to platform state
                final ReadableStates states = state.getReadableStates(FreezeService.NAME);
                final ReadableSingletonState<Timestamp> freezeTime =
                        states.getSingleton(FreezeServiceImpl.FREEZE_TIME_KEY);
                requireNonNull(freezeTime.get());
                final Instant freezeTimeInstant = Instant.ofEpochSecond(
                        freezeTime.get().seconds(), freezeTime.get().nanos());
                logger.info("Freeze time will be {}", freezeTimeInstant);
                platformState.setFreezeTime(freezeTimeInstant);
            } else if (freezeType == FREEZE_ABORT) {
                logger.info("Aborting freeze");
                // copy freeze state (which is null) to platform state
                // we just set platform state to null
                platformState.setFreezeTime(null);
            }
            // else for other freeze types, do nothing
        }
    }
}
