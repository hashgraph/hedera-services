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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ABORT;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ONLY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.FREEZE_TIME_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
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
public class PlatformStateUpdates {
    private static final Logger logger = LogManager.getLogger(PlatformStateUpdates.class);

    /**
     * Creates a new instance of this class.
     */
    @Inject
    public PlatformStateUpdates() {
        // For dagger
    }

    /**
     * Checks whether the given transaction body is a freeze transaction and eventually
     * notifies the registered facility.
     *
     * @param state the current state
     * @param txBody the transaction body
     * @param config the configuration
     */
    public void handleTxBody(
            @NonNull final State state, @NonNull final TransactionBody txBody, @NonNull final Configuration config) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(config, "config must not be null");

        if (txBody.hasFreeze()) {
            final FreezeType freezeType = txBody.freezeOrThrow().freezeType();
            final var platformStateStore =
                    new WritablePlatformStateStore(state.getWritableStates(PlatformStateService.NAME));
            if (freezeType == FREEZE_UPGRADE || freezeType == FREEZE_ONLY) {
                logger.info("Transaction freeze of type {} detected", freezeType);
                if (freezeType == FREEZE_UPGRADE) {
                    final var keyCandidateRoster =
                            config.getConfigData(TssConfig.class).keyCandidateRoster();
                    final var useRosterLifecycle =
                            config.getConfigData(AddressBookConfig.class).useRosterLifecycle();
                    if (!keyCandidateRoster && useRosterLifecycle) {
                        final var nodeStore =
                                new ReadableNodeStoreImpl(state.getReadableStates(AddressBookService.NAME));
                        final var rosterStore = new WritableRosterStore(state.getWritableStates(RosterService.NAME));
                        final var tssStore = new WritableTssStore(state.getWritableStates(TssBaseService.NAME));
                        final var candidateRoster = nodeStore.snapshotOfFutureRoster();
                        rosterStore.putCandidateRoster(candidateRoster);

                        // remove TssEncryptionKeys from state if not present in both active and candidate roster's
                        // entries
                        tssStore.removeIfNotPresent(rosterStore.getCombinedRosterEntriesNodeIds());
                    }
                }
                // copy freeze state to platform state
                final ReadableStates states = state.getReadableStates(FreezeService.NAME);
                final ReadableSingletonState<Timestamp> freezeTimeState = states.getSingleton(FREEZE_TIME_KEY);
                final var freezeTime = requireNonNull(freezeTimeState.get());
                final Instant freezeTimeInstant = Instant.ofEpochSecond(freezeTime.seconds(), freezeTime.nanos());
                logger.info("Freeze time will be {}", freezeTimeInstant);
                platformStateStore.setFreezeTime(freezeTimeInstant);
            } else if (freezeType == FREEZE_ABORT) {
                logger.info("Aborting freeze");
                platformStateStore.setFreezeTime(null);
            }
        }
    }
}
