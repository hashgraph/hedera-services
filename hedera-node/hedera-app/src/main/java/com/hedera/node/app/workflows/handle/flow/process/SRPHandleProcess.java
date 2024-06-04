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

package com.hedera.node.app.workflows.handle.flow.process;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.flow.dispatcher.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatcher.UserTransactionComponent;
import com.hedera.node.app.workflows.handle.flow.future.ScheduleServiceCronLogic;
import com.hedera.node.app.workflows.handle.flow.infra.UserTxnLogger;
import com.hedera.node.app.workflows.handle.flow.infra.records.UserRecordInitializer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SRPHandleProcess implements HandleProcess {
    private static final Logger logger = LogManager.getLogger(SRPHandleProcess.class);

    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final BlockRecordManager blockRecordManager;
    private final ScheduleServiceCronLogic scheduleServiceCronLogic;
    private final UserRecordInitializer userRecordInitializer;
    private final UserTxnLogger userTxnLogger;
    private final Provider<ChildDispatchComponent.Factory> dispatchProvider;

    @Inject
    public SRPHandleProcess(
            final StakingPeriodTimeHook stakingPeriodTimeHook,
            final BlockRecordManager blockRecordManager,
            final ScheduleServiceCronLogic scheduleServiceCronLogic,
            final UserRecordInitializer userRecordInitializer,
            final UserTxnLogger userTxnLogger,
            final Provider<ChildDispatchComponent.Factory> dispatchProvider) {
        this.stakingPeriodTimeHook = stakingPeriodTimeHook;
        this.blockRecordManager = blockRecordManager;
        this.scheduleServiceCronLogic = scheduleServiceCronLogic;
        this.userRecordInitializer = userRecordInitializer;
        this.userTxnLogger = userTxnLogger;
        this.dispatchProvider = dispatchProvider;
    }

    @Override
    public void processUserTransaction(UserTransactionComponent userTxn) {
        processStakingPeriodTimeHook(userTxn);
        blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        scheduleServiceCronLogic.expireSchedules(blockRecordManager.consTimeOfLastHandledTxn(), userTxn);
        userTxnLogger.logUserTxn(userTxn);

        final var dispatchComponent = dispatchProvider.get().create();
        userRecordInitializer.initializeUserRecord(userTxn.recordBuilder(), userTxn.txnInfo());
    }

    private void processStakingPeriodTimeHook(UserTransactionComponent userTxn) {
        try {
            stakingPeriodTimeHook.process(userTxn.savepointStack(), userTxn.tokenContext());
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }
    }
}
