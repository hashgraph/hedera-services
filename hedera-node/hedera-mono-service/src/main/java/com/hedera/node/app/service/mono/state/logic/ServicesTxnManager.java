/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.logic;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.node.app.service.mono.context.AppsManager;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertyNames;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.staking.RewardCalculator;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.annotations.RunTopLevelTransition;
import com.hedera.node.app.service.mono.state.annotations.RunTriggeredTransition;
import com.hedera.node.app.service.mono.state.initialization.BlocklistAccountCreator;
import com.hedera.node.app.service.mono.state.migration.MigrationRecordsManager;
import com.hedera.node.app.service.mono.state.virtual.IterableStorageUtils;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ServicesTxnManager {
    private static final Logger log = LogManager.getLogger(ServicesTxnManager.class);

    private static final String ERROR_LOG_TPL = "Possibly CATASTROPHIC failure in {} :: {} ==>> {} ==>>";

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
    private final BootstrapProperties bootstrapProperties;
    private final BlocklistAccountCreator blocklistAccountCreator;

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
            final RewardCalculator rewardCalculator,
            final @NonNull BootstrapProperties bootstrapProperties,
            final @NonNull BlocklistAccountCreator blocklistAccountCreator) {
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
        this.bootstrapProperties = Objects.requireNonNull(bootstrapProperties);
        this.blocklistAccountCreator = Objects.requireNonNull(blocklistAccountCreator);
    }

    private boolean isFirstTransaction = true;
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

            if (isFirstTransaction) {
                if (bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_BLOCKLIST_ENABLED)) {
                    blocklistAccountCreator.createMissingAccounts();
                }

                // The manager will only publish migration records if the MerkleNetworkContext (in
                // state) shows that it needs to do so; our responsibility here is just to give it
                // the opportunity
                migrationRecordsManager.publishMigrationRecords(consensusTime);
                isFirstTransaction = false;
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
            if (accessor.getFunction() == ContractCall) {
                final var app = AppsManager.APPS.get(new NodeId(0));
                final var accounts = app.backingAccounts();
                final var orderedIds = new TreeSet<>(HederaLedger.ACCOUNT_ID_COMPARATOR);
                orderedIds.addAll(accounts.idSet());
                orderedIds.forEach(id -> {
                    final var account = accounts.getRef(id);
                    if (account.number() >= 1001) {
                        final var storage = app.workingState().contractStorage();
                        final var firstKey = account.getFirstContractStorageKey();
                        System.out.println("Storage for 0.0." + id.getAccountNum()
                                + " (contract? " + account.isSmartContract()
                                + ", deleted? " + account.isDeleted()
                                + ", alias=" + CommonUtils.hex(account.getAlias().toByteArray())
                                + ", balance=" + account.getBalance()
                                + ", nonce=" + account.getEthereumNonce() + "):");
                        System.out.println("  -> " + IterableStorageUtils.joinedStorageMappings(firstKey, storage));
                    }
                });
            }
            createdStreamableRecord = true;
        } catch (Exception e) {
            logContextualizedError(e, "txn commit");
            attemptRollback(accessor, consensusTime, submittingMember);
        }
    }

    private void attemptRollback(TxnAccessor accessor, Instant consensusTime, long submittingMember) {
        try {
            recordCache.setFailInvalid(txnCtx.effectivePayer(), accessor, consensusTime, submittingMember);
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
            log.error(ERROR_LOG_TPL, context, accessor.getSignedTxnWrapper(), ledger.currentChangeSet(), e);
        } catch (Exception f) {
            log.error("Possibly CATASTROPHIC failure in {}", context, e);
            log.error("Full details could not be logged", f);
        }
    }
}
