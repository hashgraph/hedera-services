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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.FREEZE_TIME_KEY;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.ReadableStakingInfoStoreImpl;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;
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

    private final BiConsumer<Roster, Path> rosterExportHelper;

    /**
     * Creates a new instance of this class.
     */
    @Inject
    public PlatformStateUpdates(@NonNull final BiConsumer<Roster, Path> rosterExportHelper) {
        this.rosterExportHelper = requireNonNull(rosterExportHelper);
    }

    /**
     * Checks whether the given transaction body is a freeze transaction and eventually
     * notifies the registered facility.
     *
     * @param state  the current state
     * @param txBody the transaction body
     * @param config the configuration
     */
    public void handleTxBody(
            @NonNull final State state, @NonNull final TransactionBody txBody, @NonNull final Configuration config) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(config, "config must not be null");

        if (txBody.hasFreeze()) {
            final var freezeType = txBody.freezeOrThrow().freezeType();
            final var platformStateStore =
                    new WritablePlatformStateStore(state.getWritableStates(PlatformStateService.NAME));
            switch (freezeType) {
                case UNKNOWN_FREEZE_TYPE, TELEMETRY_UPGRADE -> {
                    // No-op
                }
                case FREEZE_UPGRADE, FREEZE_ONLY -> {
                    logger.info("Transaction freeze of type {} detected", freezeType);
                    // Copy freeze time to platform state
                    final var states = state.getReadableStates(FreezeService.NAME);
                    final ReadableSingletonState<Timestamp> freezeTimeState = states.getSingleton(FREEZE_TIME_KEY);
                    final var freezeTime = requireNonNull(freezeTimeState.get());
                    final var freezeTimeInstant = Instant.ofEpochSecond(freezeTime.seconds(), freezeTime.nanos());
                    logger.info("Freeze time will be {}", freezeTimeInstant);
                    platformStateStore.setFreezeTime(freezeTimeInstant);
                }
                case FREEZE_ABORT -> {
                    logger.info("Aborting freeze");
                    platformStateStore.setFreezeTime(null);
                }
                case PREPARE_UPGRADE -> {
                    final var networkAdminConfig = config.getConfigData(NetworkAdminConfig.class);
                    // Even if using the roster lifecycle, we only set the candidate roster at PREPARE_UPGRADE if
                    // TSS machinery is not creating candidate rosters and keying them at stake period boundaries
                    final var addressBookConfig = config.getConfigData(AddressBookConfig.class);
                    if (addressBookConfig.useRosterLifecycle()
                            && addressBookConfig.createCandidateRosterOnPrepareUpgrade()) {
                        logger.info("Creating candidate roster at PREPARE_UPGRADE");
                        final var nodeStore =
                                new ReadableNodeStoreImpl(state.getReadableStates(AddressBookService.NAME));
                        final var rosterStore = new WritableRosterStore(state.getWritableStates(RosterService.NAME));
                        final var stakingInfoStore =
                                new ReadableStakingInfoStoreImpl(state.getReadableStates(TokenService.NAME));
                        final var candidateRoster = nodeStore.snapshotOfFutureRoster();
                        logger.info("Candidate roster is {}", candidateRoster);
                        boolean rosterAccepted = false;
                        try {
                            rosterStore.putCandidateRoster(candidateRoster);
                            rosterAccepted = true;
                        } catch (Exception e) {
                            logger.warn("Candidate roster was rejected", e);
                        }
                        if (rosterAccepted) {
                            // If the candidate roster needs to be exported, export the file
                            if (networkAdminConfig.exportCandidateRoster()) {
                                doExport(candidateRoster, networkAdminConfig);
                            } else {
                                // When the candidate roster is not exported, update the candidate
                                // roster weights with weights from stakingNodeInfo map
                                logger.info("Updating candidate roster weights");
                                updateCandidateRosterWeights(candidateRoster, stakingInfoStore, rosterStore);
                            }
                        }
                    } else if (networkAdminConfig.exportCandidateRoster()) {
                        // Having the option to export candidate-roster.json even before using the roster
                        // lifecycle simplifies test automation in the adoption period
                        final var nodeStore =
                                new ReadableNodeStoreImpl(state.getReadableStates(AddressBookService.NAME));
                        final var candidateRoster = new Roster(StreamSupport.stream(
                                        Spliterators.spliterator(nodeStore.keys(), nodeStore.sizeOfState(), DISTINCT),
                                        false)
                                .mapToLong(EntityNumber::number)
                                .sorted()
                                .mapToObj(nodeStore::get)
                                .filter(node -> node != null && !node.deleted())
                                .map(node -> new RosterEntry(
                                        node.nodeId(),
                                        node.weight(),
                                        node.gossipCaCertificate(),
                                        List.of(
                                                node.gossipEndpoint().getLast(),
                                                node.gossipEndpoint().getFirst())))
                                .toList());
                        doExport(candidateRoster, networkAdminConfig);
                    }
                }
            }
        }
    }

    /**
     * Updates the candidate roster weights with the weights from the staking info store.
     * If the staking info is not available for a node, the weight is set to 0. The updated
     * candidate roster is then stored in the roster store.
     *
     * @param candidateRoster the candidate roster
     * @param stakingInfoStore the staking info store
     * @param rosterStore the roster store
     */
    private void updateCandidateRosterWeights(
            @NonNull final Roster candidateRoster,
            @NonNull final ReadableStakingInfoStoreImpl stakingInfoStore,
            @NonNull final WritableRosterStore rosterStore) {
        final var newEntries = candidateRoster.rosterEntries().stream()
                .map(entry -> {
                    final var nodeId = entry.nodeId();
                    final var stakingInfo = stakingInfoStore.get(nodeId);
                    long weight = 0;
                    if (stakingInfo != null) {
                        weight = stakingInfo.stake();
                    }
                    return entry.copyBuilder().weight(weight).build();
                })
                .toList();
        rosterStore.putCandidateRoster(
                candidateRoster.copyBuilder().rosterEntries(newEntries).build());
    }

    private void doExport(@NonNull final Roster candidateRoster, @NonNull final NetworkAdminConfig networkAdminConfig) {
        final var exportPath = Paths.get(networkAdminConfig.candidateRosterExportFile());
        logger.info("Exporting candidate roster after PREPARE_UPGRADE to '{}'", exportPath.toAbsolutePath());
        rosterExportHelper.accept(candidateRoster, exportPath);
    }
}
