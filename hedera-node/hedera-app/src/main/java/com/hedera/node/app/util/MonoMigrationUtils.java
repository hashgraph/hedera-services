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

package com.hedera.node.app.util;

import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.ACCOUNTS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.CONTRACT_STORAGE;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.NETWORK_CTX;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.RECORD_STREAM_RUNNING_HASH;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.SCHEDULE_TXS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.STAKING_INFO;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKENS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKEN_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.UNIQUE_TOKENS;
import static com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint.MONO_PRE_MIGRATION;
import static com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint.selectedDumpCheckpoints;
import static com.hedera.node.app.service.mono.statedumpers.StateDumper.dumpMonoChildrenFrom;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;

import com.hedera.node.app.fees.schemas.V0490FeeSchema;
import com.hedera.node.app.ids.schemas.V0490EntityIdSchema;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema;
import com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.Schema;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for migrating from mono to modular services.
 */
public class MonoMigrationUtils {
    private static final Logger logger = LogManager.getLogger(MonoMigrationUtils.class);

    /**
     * In order to migrate from mono to modular, we need to keep references to the virtual maps that we are migrating.
     * Then they can be properly closed when the migration is complete.
     */
    private static final Set<VirtualMap<?, ?>> MONO_VIRTUAL_MAPS = ConcurrentHashMap.newKeySet();

    private MonoMigrationUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * If the state is a {@link MerkleHederaState}, and its first child is a {@link VirtualMap},
     * attempts to extract all data needed to initialize the modular services from the mono state
     * and set it in static fields of the appropriate {@link Schema} implementations.
     *
     * <p>Keeps all virtual maps in {@link #MONO_VIRTUAL_MAPS} so they can be closed after migration.
     *
     * @param hederaState the state to maybe migrate
     * @param trigger the trigger for the migration
     * @param metrics the metrics to register the virtual maps with
     */
    public static void maybeMigrateFrom(
            @NonNull final HederaState hederaState,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics) {
        if (hederaState instanceof MerkleHederaState state) {
            if (state.getChild(0) instanceof VirtualMap<?, ?>) {
                try {
                    if (shouldDump(trigger, MONO_PRE_MIGRATION)) {
                        dumpMonoChildrenFrom(state, MONO_PRE_MIGRATION);
                    }
                } catch (Exception e) {
                    logger.error("Failed to dump mono state before migration at MONO_PRE_MIGRATION", e);
                }

                // --------------------- BEGIN MONO -> MODULAR MIGRATION ---------------------
                logger.info("BBM: migration beginning 😅...");

                // --------------------- UNIQUE_TOKENS (0)
                final VirtualMap<UniqueTokenKey, UniqueTokenValue> uniqTokensFromState = state.getChild(UNIQUE_TOKENS);
                if (uniqTokensFromState != null) {
                    // Copy this virtual map, so it doesn't get released before the migration is done
                    final var copy = uniqTokensFromState.copy();
                    copy.registerMetrics(metrics);
                    MONO_VIRTUAL_MAPS.add(copy);
                    V0490TokenSchema.setNftsFromState(uniqTokensFromState);
                }

                // --------------------- TOKEN_ASSOCIATIONS (1)
                final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> tokenRelsFromState =
                        state.getChild(TOKEN_ASSOCIATIONS);
                if (tokenRelsFromState != null) {
                    // Copy this virtual map, so it doesn't get released before the migration is done
                    final var copy = tokenRelsFromState.copy();
                    copy.registerMetrics(metrics);
                    MONO_VIRTUAL_MAPS.add(copy);
                    V0490TokenSchema.setTokenRelsFromState(tokenRelsFromState);
                }

                // --------------------- ACCOUNTS (4)
                final VirtualMap<EntityNumVirtualKey, OnDiskAccount> acctsFromState = state.getChild(ACCOUNTS);
                if (acctsFromState != null) {
                    // Copy this virtual map, so it doesn't get released before the migration is done
                    final var copy = acctsFromState.copy();
                    copy.registerMetrics(metrics);
                    MONO_VIRTUAL_MAPS.add(copy);
                    V0490TokenSchema.setAcctsFromState(acctsFromState);
                }

                // --------------------- TOKENS (5)
                final MerkleMap<EntityNum, MerkleToken> tokensFromState = state.getChild(TOKENS);
                if (tokensFromState != null) {
                    V0490TokenSchema.setTokensFromState(tokensFromState);
                }

                // --------------------- NETWORK_CTX (6)
                // Here we assign the network context, but don't migrate it by itself. These properties have been split
                // out
                // to various services in the modular code, and will each be migrated in its appropriate service.
                final MerkleNetworkContext fromNetworkContext = state.getChild(NETWORK_CTX);

                // --------------------- SPECIAL_FILES (7)
                // No longer useful; don't migrate

                // --------------------- SCHEDULE_TXS (8)
                final MerkleScheduledTransactions scheduleFromState = state.getChild(SCHEDULE_TXS);
                if (scheduleFromState != null) {
                    V0490ScheduleSchema.setFs(scheduleFromState);
                }

                // --------------------- RECORD_STREAM_RUNNING_HASH (9)
                final RecordsRunningHashLeaf blockInfoFromState = state.getChild(RECORD_STREAM_RUNNING_HASH);
                if (blockInfoFromState != null) {
                    V0490BlockRecordSchema.setFs(blockInfoFromState);
                    V0490BlockRecordSchema.setMnc(fromNetworkContext);
                }

                // --------------------- LEGACY_ADDRESS_BOOK (10)
                // Not using anywhere; won't be migrated

                // --------------------- CONTRACT_STORAGE (11)
                final VirtualMap<ContractKey, IterableContractValue> contractFromStorage =
                        state.getChild(CONTRACT_STORAGE);
                if (contractFromStorage != null) {
                    // Copy this virtual map, so it doesn't get released before the migration is done
                    final var copy = contractFromStorage.copy();
                    copy.registerMetrics(metrics);
                    MONO_VIRTUAL_MAPS.add(copy);
                    V0490ContractSchema.setStorageFromState(VirtualMapLike.from(contractFromStorage));
                }

                // --------------------- STAKING_INFO (12)
                final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfoFromState = state.getChild(STAKING_INFO);
                if (stakingInfoFromState != null) {
                    V0490TokenSchema.setStakingFs(stakingInfoFromState, fromNetworkContext);
                }

                // --------------------- PAYER_RECORDS_OR_CONSOLIDATED_FCQ (13)
                final FCQueue<ExpirableTxnRecord> fcqFromState = state.getChild(PAYER_RECORDS_OR_CONSOLIDATED_FCQ);
                if (fcqFromState != null) {
                    V0490RecordCacheSchema.setFromRecs(new ArrayList<>(fcqFromState));
                }

                // --------------------- Midnight Rates (separate service in modular code - fee service)
                if (fromNetworkContext != null) {
                    V0490FeeSchema.setFs(fromNetworkContext.getMidnightRates());
                }

                // --------------------- Sequence Number (separate service in modular code - entity ID service)
                if (fromNetworkContext != null) {
                    V0490EntityIdSchema.setFs(fromNetworkContext.seqNo().current());
                }

                // --------------------- CONGESTION THROTTLE SERVICE (14)
                if (fromNetworkContext != null) {
                    V0490CongestionThrottleSchema.setUsageSnapshots(fromNetworkContext.usageSnapshots());
                    V0490CongestionThrottleSchema.setGasThrottleUsageSnapshot(
                            fromNetworkContext.getGasThrottleUsageSnapshot());
                    V0490FreezeSchema.setFs(true);
                }

                // Here we release all mono children so that we don't have a bunch of null routes in state
                state.addDeserializedChildren(List.of(), 0);

                // --------------------- END OF MONO -> MODULAR MIGRATION ---------------------
            }
        } else {
            throw new IllegalArgumentException("Unsupported state type");
        }
    }

    /**
     * Releases all virtual maps that were copied during migration (if the node
     * just started from a mono-service state).
     */
    public static void maybeReleaseMaps() {
        MONO_VIRTUAL_MAPS.forEach(vm -> {
            if (!vm.isDestroyed()) {
                vm.release();
            }
        });
        MONO_VIRTUAL_MAPS.clear();
    }

    public static boolean shouldDump(@NonNull final InitTrigger trigger, @NonNull final DumpCheckpoint checkpoint) {
        return trigger == EVENT_STREAM_RECOVERY && selectedDumpCheckpoints().contains(checkpoint);
    }
}
