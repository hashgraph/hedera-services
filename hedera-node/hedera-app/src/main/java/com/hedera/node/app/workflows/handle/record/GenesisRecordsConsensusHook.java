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
import static com.hedera.hapi.util.HapiUtils.ACCOUNT_ID_COMPARATOR;
import static com.hedera.hapi.util.HapiUtils.FUNDING_ACCOUNT_EXPIRY;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.schemas.SyntheticAccountCreator;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for storing the system accounts created during node startup, and then creating
 * the corresponding synthetic records when a consensus time becomes available.
 */
@Singleton
public class GenesisRecordsConsensusHook {
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
    private final SyntheticAccountCreator syntheticAccountCreator;

    /**
     * Constructs a new {@link GenesisRecordsConsensusHook}.
     */
    @Inject
    public GenesisRecordsConsensusHook(@NonNull final SyntheticAccountCreator syntheticAccountCreator) {
        this.syntheticAccountCreator = requireNonNull(syntheticAccountCreator);
    }

    /**
     * Called only once, before handling the first transaction in network history.
     *
     * @throws NullPointerException if called more than once
     */
    public void process(@NonNull final TokenContext context) {
        final var firstConsensusTime = context.consensusTime();
        log.info("Exporting genesis records at {}", firstConsensusTime);
        // The account creator registers all its synthetics accounts based on the
        // given genesis configuration via callbacks on this object, so following
        // references to our field sets are guaranteed to be non-empty as appropriate
        syntheticAccountCreator.generateSyntheticAccounts(
                context.configuration(),
                this::systemAccounts,
                this::stakingAccounts,
                this::treasuryClones,
                this::miscAccounts,
                this::blocklistAccounts);

        if (!systemAccounts.isEmpty()) {
            createAccountRecordBuilders(systemAccounts, context, SYSTEM_ACCOUNT_CREATION_MEMO);
            log.info(" - Queued {} system account records", systemAccounts.size());
            systemAccounts = null;
        }

        if (!stakingAccounts.isEmpty()) {
            final var implicitAutoRenewPeriod = FUNDING_ACCOUNT_EXPIRY - firstConsensusTime.getEpochSecond();
            createAccountRecordBuilders(stakingAccounts, context, STAKING_MEMO, implicitAutoRenewPeriod);
            log.info(" - Queued {} staking account records", stakingAccounts.size());
            stakingAccounts = null;
        }

        if (!miscAccounts.isEmpty()) {
            createAccountRecordBuilders(miscAccounts, context, null);
            log.info("Queued {} misc account records", miscAccounts.size());
            miscAccounts = null;
        }

        if (!treasuryClones.isEmpty()) {
            createAccountRecordBuilders(treasuryClones, context, TREASURY_CLONE_MEMO);
            log.info("Queued {} treasury clone account records", treasuryClones.size());
            treasuryClones = null;
        }

        if (!blocklistAccounts.isEmpty()) {
            createAccountRecordBuilders(blocklistAccounts, context, null);
            log.info("Queued {} blocklist account records", blocklistAccounts.size());
            blocklistAccounts = null;
        }
    }

    private void systemAccounts(@NonNull final SortedSet<Account> accounts) {
        requireNonNull(systemAccounts, "Genesis records already exported").addAll(requireNonNull(accounts));
    }

    private void stakingAccounts(@NonNull final SortedSet<Account> accounts) {
        requireNonNull(stakingAccounts, "Genesis records already exported").addAll(requireNonNull(accounts));
    }

    private void miscAccounts(@NonNull final SortedSet<Account> accounts) {
        requireNonNull(miscAccounts, "Genesis records already exported").addAll(requireNonNull(accounts));
    }

    private void treasuryClones(@NonNull final SortedSet<Account> accounts) {
        requireNonNull(treasuryClones, "Genesis records already exported").addAll(requireNonNull(accounts));
    }

    private void blocklistAccounts(@NonNull final SortedSet<Account> accounts) {
        requireNonNull(blocklistAccounts, "Genesis records already exported").addAll(requireNonNull(accounts));
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
}
