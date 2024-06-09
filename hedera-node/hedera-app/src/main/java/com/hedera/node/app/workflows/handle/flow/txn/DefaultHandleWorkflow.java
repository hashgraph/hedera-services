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

package com.hedera.node.app.workflows.handle.flow.txn;

import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.flow.dispatch.logic.DispatchProcessor;
import com.hedera.node.app.workflows.handle.flow.txn.logic.HollowAccountCompleter;
import com.hedera.node.app.workflows.handle.flow.txn.logic.SchedulePurger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class DefaultHandleWorkflow {
    private static final Logger logger = LogManager.getLogger(DefaultHandleWorkflow.class);

    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final BlockRecordManager blockRecordManager;
    private final SchedulePurger schedulePurger;
    private final DispatchProcessor dispatchProcessor;
    private final HollowAccountCompleter hollowAccountFinalization;

    @Inject
    public DefaultHandleWorkflow(
            final StakingPeriodTimeHook stakingPeriodTimeHook,
            final BlockRecordManager blockRecordManager,
            final SchedulePurger schedulePurger,
            final DispatchProcessor dispatchProcessor,
            final HollowAccountCompleter hollowAccountFinalization) {
        this.stakingPeriodTimeHook = stakingPeriodTimeHook;
        this.blockRecordManager = blockRecordManager;
        this.schedulePurger = schedulePurger;
        this.dispatchProcessor = dispatchProcessor;
        this.hollowAccountFinalization = hollowAccountFinalization;
    }

    public WorkDone execute(UserTransactionComponent userTxn) {
        processStakingPeriodTimeHook(userTxn);
        blockRecordManager.advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        schedulePurger.expireSchedules(userTxn);

        logUserTxn(userTxn);

        final var userDispatch = userTxn.userDispatchProvider().get().create();
        hollowAccountFinalization.finalizeHollowAccounts(userTxn, userDispatch);

        return dispatchProcessor.processDispatch(userDispatch);
    }

    private void processStakingPeriodTimeHook(UserTransactionComponent userTxn) {
        try {
            stakingPeriodTimeHook.process(userTxn.stack(), userTxn.tokenContext());
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }
    }

    public void logUserTxn(UserTransactionComponent userTxn) {
        // Log start of user transaction to transaction state log
        logStartUserTransaction(
                userTxn.platformTxn(),
                userTxn.txnInfo().txBody(),
                userTxn.txnInfo().payerID());
        logStartUserTransactionPreHandleResultP2(userTxn.preHandleResult());
        logStartUserTransactionPreHandleResultP3(userTxn.preHandleResult());
    }
}
