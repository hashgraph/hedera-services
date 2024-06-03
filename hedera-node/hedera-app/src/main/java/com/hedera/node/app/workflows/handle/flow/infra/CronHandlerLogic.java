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

package com.hedera.node.app.workflows.handle.flow.infra;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.state.HederaState;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class CronHandlerLogic {
    private static final Logger logger = LogManager.getLogger(CronHandlerLogic.class);

    private final GenesisRecordsConsensusHook genesisRecordsConsensusHook;
    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final ScheduleExpirationHook scheduleExpirationHook;
    private final BlockRecordManager blockRecordManager;
    private final TokenContext tokenContext;
    private final Instant consensusNow;
    private final HederaState state;
    private final ConfigProvider configProvider;
    private final StoreMetricsService storeMetricsService;

    @Inject
    public CronHandlerLogic(
            final GenesisRecordsConsensusHook genesisRecordsConsensusHook,
            final StakingPeriodTimeHook stakingPeriodTimeHook,
            final ScheduleExpirationHook scheduleExpirationHook,
            final BlockRecordManager blockRecordManager,
            final TokenContext tokenContext,
            final Instant consensusNow,
            final HederaState state,
            final ConfigProvider configProvider,
            final StoreMetricsService storeMetricsService) {
        this.genesisRecordsConsensusHook = genesisRecordsConsensusHook;
        this.stakingPeriodTimeHook = stakingPeriodTimeHook;
        this.scheduleExpirationHook = scheduleExpirationHook;
        this.blockRecordManager = blockRecordManager;
        this.tokenContext = tokenContext;
        this.consensusNow = consensusNow;
        this.state = state;
        this.configProvider = configProvider;
        this.storeMetricsService = storeMetricsService;
    }

    public void processCronJobs(SavepointStackImpl stack) {
        // Do any one-time work for the first transaction after genesis;
        // overhead for all following transactions is effectively zero
        genesisRecordsConsensusHook.process(tokenContext);
        try {
            // If this is the first user transaction after midnight, then handle staking updates prior to handling the
            // transaction itself.
            stakingPeriodTimeHook.process(stack, tokenContext);
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }
        // Consensus hooks have now had a chance to publish any records from migrations; therefore we can begin handling
        // the user transaction
        blockRecordManager.advanceConsensusClock(consensusNow, state);
        // Look for any expired schedules and delete them when new block is created
        final var firstSecondToExpire =
                blockRecordManager.firstConsTimeOfLastBlock().getEpochSecond();
        final var lastSecondToExpire = consensusNow.getEpochSecond();
        final var scheduleStore = new WritableStoreFactory(
                        stack, ScheduleService.NAME, configProvider.getConfiguration(), storeMetricsService)
                .getStore(WritableScheduleStore.class);
        // purge all expired schedules between the first consensus time of last block and the current consensus time
        scheduleExpirationHook.processExpiredSchedules(scheduleStore, firstSecondToExpire, lastSecondToExpire);
    }
}
