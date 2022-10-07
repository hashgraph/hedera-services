/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.records.RecordCache;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.annotations.RunTopLevelTransition;
import com.hedera.services.state.annotations.RunTriggeredTransition;
import com.hedera.services.state.migration.MigrationRecordsManager;
import com.hedera.services.utils.accessors.TxnAccessor;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ServicesTxnManager {
    private static final Logger log = LogManager.getLogger(ServicesTxnManager.class);

    private static final String ERROR_LOG_TPL =
            "Possibly CATASTROPHIC failure in {} :: {} ==>> {} ==>>";

    private final Runnable scopedProcessing;
    private final Runnable scopedTriggeredProcessing;
    private final RecordCache recordCache;
    private final HederaLedger ledger;
    private final TransactionContext txnCtx;
    private final SigImpactHistorian sigImpactHistorian;
    private final RecordsHistorian recordsHistorian;
    private final MigrationRecordsManager migrationRecordsManager;
    private final RecordStreaming recordStreaming;
    private final BlockManager blockManager;
    private final RewardCalculator rewardCalculator;

    @Inject
    public ServicesTxnManager(
            final @RunTopLevelTransition Runnable scopedProcessing,
            final @RunTriggeredTransition Runnable scopedTriggeredProcessing,
            final RecordCache recordCache,
            final HederaLedger ledger,
            final TransactionContext txnCtx,
            final SigImpactHistorian sigImpactHistorian,
            final RecordsHistorian recordsHistorian,
            final MigrationRecordsManager migrationRecordsManager,
            final RecordStreaming recordStreaming,
            final BlockManager blockManager,
            final RewardCalculator rewardCalculator) {
        this.txnCtx = txnCtx;
        this.ledger = ledger;
        this.recordCache = recordCache;
        this.recordStreaming = recordStreaming;
        this.recordsHistorian = recordsHistorian;
        this.scopedProcessing = scopedProcessing;
        this.sigImpactHistorian = sigImpactHistorian;
        this.migrationRecordsManager = migrationRecordsManager;
        this.scopedTriggeredProcessing = scopedTriggeredProcessing;
        this.blockManager = blockManager;
        this.rewardCalculator = rewardCalculator;
    }

    private boolean needToPublishMigrationRecords = true;
    private boolean createdStreamableRecord;

    public void process(TxnAccessor accessor, Instant consensusTime, long submittingMember) {
        var processFailed = false;
        createdStreamableRecord = false;

        try {
            txnCtx.resetFor(accessor, consensusTime, submittingMember);
            sigImpactHistorian.setChangeTime(consensusTime);
            recordsHistorian.clearHistory();
            blockManager.reset();
            rewardCalculator.reset();
            ledger.begin();

            if (needToPublishMigrationRecords) {
                // The manager will only publish migration records if the MerkleNetworkContext (in
                // state) shows that it needs to do so; our responsibility here is just to give it
                // the opportunity
                migrationRecordsManager.publishMigrationRecords(consensusTime);
                needToPublishMigrationRecords = false;
            }
            if (accessor.isTriggeredTxn()) {
                scopedTriggeredProcessing.run();
            } else {
                scopedProcessing.run();
            }
        } catch (Exception processFailure) {
            processFailed = true;
            logContextualizedError(processFailure, "txn processing");
            txnCtx.setStatus(FAIL_INVALID);
        }

        if (processFailed) {
            attemptRollback(accessor, consensusTime, submittingMember);
        } else {
            attemptCommit(accessor, consensusTime, submittingMember);
            if (createdStreamableRecord) {
                attemptRecordStreaming();
            }
        }
    }

    private void attemptRecordStreaming() {
        try {
            recordStreaming.streamUserTxnRecords();
        } catch (Exception e) {
            logContextualizedError(e, "record streaming");
        }
    }

    private void attemptCommit(TxnAccessor accessor, Instant consensusTime, long submittingMember) {
        try {
            ledger.commit();
            createdStreamableRecord = true;
        } catch (Exception e) {
            logContextualizedError(e, "txn commit");
            attemptRollback(accessor, consensusTime, submittingMember);
        }
    }

    private void attemptRollback(
            TxnAccessor accessor, Instant consensusTime, long submittingMember) {
        try {
            recordCache.setFailInvalid(
                    txnCtx.effectivePayer(), accessor, consensusTime, submittingMember);
        } catch (Exception e) {
            logContextualizedError(e, "failure record creation");
        }
        try {
            ledger.rollback();
        } catch (Exception e) {
            logContextualizedError(e, "txn rollback");
        }
    }

    private void logContextualizedError(Exception e, String context) {
        try {
            final var accessor = txnCtx.accessor();
            log.error(
                    ERROR_LOG_TPL,
                    context,
                    accessor.getSignedTxnWrapper(),
                    ledger.currentChangeSet(),
                    e);
        } catch (Exception f) {
            log.error("Possibly CATASTROPHIC failure in {}", context, e);
            log.error("Full details could not be logged", f);
        }
    }
}
