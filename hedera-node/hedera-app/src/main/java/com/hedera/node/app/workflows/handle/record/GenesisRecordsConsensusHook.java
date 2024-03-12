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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.spi.HapiUtils.ACCOUNT_ID_COMPARATOR;
import static com.hedera.node.app.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for storing the system accounts created during node startup, and then creating
 * the corresponding synthetic records when a consensus time becomes available.
 */
@Singleton
public class GenesisRecordsConsensusHook implements GenesisRecordsBuilder {
    private static final Logger log = LogManager.getLogger(GenesisRecordsConsensusHook.class);
    private static final String SYSTEM_ACCOUNT_CREATION_MEMO = "Synthetic system creation";
    private static final String STAKING_MEMO = "Release 0.24.1 migration record";
    private static final String TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";
    private static final Comparator<Account> ACCOUNT_COMPARATOR =
            Comparator.comparing(Account::accountId, ACCOUNT_ID_COMPARATOR);

    private SortedSet<Account> systemAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
    private SortedSet<Account> stakingAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
    private SortedSet<Account> miscAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
    private SortedSet<Account> treasuryClones = new TreeSet<>(ACCOUNT_COMPARATOR);
    private SortedSet<Account> blocklistAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);

    /**
     * <b> ⚠️⚠️ Note: though this method will be called each time a new platform event is received,
     * the records created by this class should only be created once.</b> After each data structure's
     * account records are created, each corresponding data structure is intentionally emptied ⚠️⚠️
     * <p>
     * It would be great if we could find a way to not have to invoke this method multiple times...
     */
    public void process(@NonNull final TokenContext context) {
        final var blockStore = context.readableStore(ReadableBlockRecordStore.class);

        // This process should only run ONCE, when a node receives its first transaction after startup
        if (!shouldStreamRecords(blockStore, context)) return;

        final var consensusTime = context.consensusTime();
        boolean recordsStreamed = false;

        final var numSysAccts = systemAccounts.size();
        if (!systemAccounts.isEmpty()) {
            createAccountRecordBuilders(systemAccounts, context, SYSTEM_ACCOUNT_CREATION_MEMO);
            systemAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
            recordsStreamed = true;
        }
        log.info("Queued {} system account records with consTime {}", numSysAccts, consensusTime);

        final var numStakingAccts = stakingAccounts.size();
        if (!stakingAccounts.isEmpty()) {
            final var implicitAutoRenewPeriod = FUNDING_ACCOUNT_EXPIRY - consensusTime.getEpochSecond();
            createAccountRecordBuilders(stakingAccounts, context, STAKING_MEMO, implicitAutoRenewPeriod);
            stakingAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
            recordsStreamed = true;
        }
        log.info("Queued {} staking account records with consTime {}", numStakingAccts, consensusTime);

        final var numMiscAccts = miscAccounts.size();
        if (!miscAccounts.isEmpty()) {
            createAccountRecordBuilders(miscAccounts, context, null);
            miscAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
            recordsStreamed = true;
        }
        log.info("Queued {} misc account records with consTime {}", numMiscAccts, consensusTime);

        final var numTreasuryClones = treasuryClones.size();
        if (!treasuryClones.isEmpty()) {
            createAccountRecordBuilders(treasuryClones, context, TREASURY_CLONE_MEMO);
            treasuryClones = new TreeSet<>(ACCOUNT_COMPARATOR);
            recordsStreamed = true;
        }
        log.info("Queued {} treasury clone account records with consTime {}", numTreasuryClones, consensusTime);

        final var numBlocklistAccts = blocklistAccounts.size();
        if (!blocklistAccounts.isEmpty()) {
            createAccountRecordBuilders(blocklistAccounts, context, null);
            blocklistAccounts = new TreeSet<>(ACCOUNT_COMPARATOR);
            recordsStreamed = true;
        }
        log.info("Queued {} blocklist account records with consTime {}", numBlocklistAccts, consensusTime);

        if (recordsStreamed) {
            context.markMigrationRecordsStreamed();
        }
    }

    @Override
    public void systemAccounts(@NonNull final SortedSet<Account> accounts) {
        systemAccounts.addAll(requireNonNull(accounts));
    }

    @Override
    public void stakingAccounts(@NonNull final SortedSet<Account> accounts) {
        stakingAccounts.addAll(requireNonNull(accounts));
    }

    @Override
    public void miscAccounts(@NonNull final SortedSet<Account> accounts) {
        miscAccounts.addAll(requireNonNull(accounts));
    }

    @Override
    public void treasuryClones(@NonNull final SortedSet<Account> accounts) {
        treasuryClones.addAll(requireNonNull(accounts));
    }

    @Override
    public void blocklistAccounts(@NonNull final SortedSet<Account> accounts) {
        blocklistAccounts.addAll(requireNonNull(accounts));
    }

    private void createAccountRecordBuilders(
            @NonNull final SortedSet<Account> map,
            @NonNull final TokenContext context,
            @Nullable final String recordMemo) {
        createAccountRecordBuilders(map, context, recordMemo, null);
    }

    private void createAccountRecordBuilders(
            @NonNull final SortedSet<Account> accts,
            @NonNull final TokenContext context,
            @Nullable final String recordMemo,
            @Nullable final Long overrideAutoRenewPeriod) {
        for (final Account account : accts) {
            // we create preceding records on genesis for each system account created.
            // This is an exception and should not fail with MAX_CHILD_RECORDS_EXCEEDED
            final var recordBuilder =
                    context.addUncheckedPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class);
            recordBuilder.accountID(account.accountId());
            if (recordMemo != null) {
                recordBuilder.memo(recordMemo);
            }

            final var op = newCryptoCreate(account);
            if (overrideAutoRenewPeriod != null) {
                op.autoRenewPeriod(Duration.newBuilder().seconds(overrideAutoRenewPeriod));
            }
            final var body =
                    TransactionBody.newBuilder().cryptoCreateAccount(op).build();
            recordBuilder.transaction(transactionWith(body));

            final var balance = account.tinybarBalance();
            if (balance != 0) {
                var accountID = AccountID.newBuilder()
                        .accountNum(account.accountId().accountNumOrElse(0L))
                        .shardNum(account.accountId().shardNum())
                        .realmNum(account.accountId().realmNum())
                        .build();

                recordBuilder.transferList(TransferList.newBuilder()
                        .accountAmounts(asAccountAmounts(Map.of(accountID, balance)))
                        .build());
            }
            recordBuilder.status(SUCCESS);

            log.debug("Queued synthetic CryptoCreate for {} account {}", recordMemo, account);
        }
    }

    private static CryptoCreateTransactionBody.Builder newCryptoCreate(@NonNull final Account account) {
        return CryptoCreateTransactionBody.newBuilder()
                .key(account.key())
                .memo(account.memo())
                .declineReward(account.declineReward())
                .receiverSigRequired(account.receiverSigRequired())
                .autoRenewPeriod(Duration.newBuilder()
                        .seconds(account.autoRenewSeconds())
                        .build())
                .initialBalance(account.tinybarBalance())
                .alias(account.alias());
    }

    private static boolean shouldStreamRecords(
            @NonNull final ReadableBlockRecordStore blockStore, @NonNull final TokenContext context) {
        // ONLY stream actual records when:
        // 1. This is the first transaction after startup, and
        // 2. We haven't streamed any migration records yet
        return context.isFirstTransaction() && !blockStore.getLastBlockInfo().migrationRecordsStreamed();
    }
}
