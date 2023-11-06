/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.workflows.handle.ConsensusTimeHook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is responsible for storing the system accounts created during node startup, and then creating
 * the corresponding synthetic records when a consensus time becomes available.
 */
@Singleton
public class GenesisRecordsConsensusHook implements GenesisRecordsBuilder, ConsensusTimeHook {
    private static final Logger log = LogManager.getLogger(GenesisRecordsConsensusHook.class);
    private static final String SYSTEM_ACCOUNT_CREATION_MEMO = "Synthetic system creation";
    private static final String STAKING_MEMO = "Release 0.24.1 migration record";
    private static final String TREASURY_CLONE_MEMO = "Synthetic zero-balance treasury clone";

    private Map<Account, CryptoCreateTransactionBody.Builder> systemAccounts = new HashMap<>();
    private Map<Account, CryptoCreateTransactionBody.Builder> stakingAccounts = new HashMap<>();
    private Map<Account, CryptoCreateTransactionBody.Builder> miscAccounts = new HashMap<>();
    private Map<Account, CryptoCreateTransactionBody.Builder> treasuryClones = new HashMap<>();
    private Map<Account, CryptoCreateTransactionBody.Builder> blocklistAccounts = new HashMap<>();

    private Instant consensusTimeOfLastHandledTxn = null;

    /**
     * <b> ⚠️⚠️ Note: though this method will be called each time a new platform event is received,
     * the records created by this class should only be created once.</b> After each data structure's
     * account records are created, each corresponding data structure is intentionally emptied ⚠️⚠️
     * <p>
     * It would be great if we could find a way to not have to invoke this method multiple times...
     */
    @Override
    public void process(@NonNull final TokenContext context) {
        // This process should only run ONCE, when a node receives its first transaction after startup
        if (consensusTimeOfLastHandledTxn != null) return;

        // First we set consensusTimeOfLastHandledTxn so that this process won't run again
        final var consensusTime = context.consensusTime();
        consensusTimeOfLastHandledTxn = consensusTime;

        if (!systemAccounts.isEmpty()) {
            createAccountRecordBuilders(systemAccounts, context, SYSTEM_ACCOUNT_CREATION_MEMO);
            systemAccounts = Collections.emptyMap();
        }

        if (!stakingAccounts.isEmpty()) {
            final var implicitAutoRenewPeriod = FUNDING_ACCOUNT_EXPIRY - consensusTime.getEpochSecond();
            createAccountRecordBuilders(stakingAccounts, context, STAKING_MEMO, implicitAutoRenewPeriod);
            stakingAccounts = Collections.emptyMap();
        }

        if (!miscAccounts.isEmpty()) {
            createAccountRecordBuilders(miscAccounts, context, null);
            miscAccounts = Collections.emptyMap();
        }

        if (!treasuryClones.isEmpty()) {
            createAccountRecordBuilders(treasuryClones, context, TREASURY_CLONE_MEMO);
            treasuryClones = Collections.emptyMap();
        }

        if (!blocklistAccounts.isEmpty()) {
            createAccountRecordBuilders(blocklistAccounts, context, null);
            blocklistAccounts = Collections.emptyMap();
        }
    }

    @Override
    public void systemAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts) {
        systemAccounts.putAll(requireNonNull(accounts));
    }

    @Override
    public void stakingAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts) {
        stakingAccounts.putAll(requireNonNull(accounts));
    }

    @Override
    public void miscAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts) {
        miscAccounts.putAll(requireNonNull(accounts));
    }

    @Override
    public void treasuryClones(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts) {
        treasuryClones.putAll(requireNonNull(accounts));
    }

    @Override
    public void blocklistAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts) {
        blocklistAccounts.putAll(requireNonNull(accounts));
    }

    @VisibleForTesting
    void setLastConsensusTime(@Nullable final Instant lastConsensusTime) {
        consensusTimeOfLastHandledTxn = lastConsensusTime;
    }

    private void createAccountRecordBuilders(
            @NonNull final Map<Account, CryptoCreateTransactionBody.Builder> map,
            @NonNull final TokenContext context,
            @Nullable final String recordMemo) {
        createAccountRecordBuilders(map, context, recordMemo, null);
    }

    private void createAccountRecordBuilders(
            @NonNull final Map<Account, CryptoCreateTransactionBody.Builder> map,
            @NonNull final TokenContext context,
            @Nullable final String recordMemo,
            @Nullable final Long overrideAutoRenewPeriod) {
        final var orderedAccts = map.keySet().stream()
                .sorted(Comparator.comparingLong(acct -> acct.accountId().accountNum()))
                .toList();
        for (final Account key : orderedAccts) {
            // we create preceding records on genesis for each system account created.
            // This is an exception and should not fail with MAX_CHILD_RECORDS_EXCEEDED
            final var recordBuilder =
                    context.addUncheckedPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class);
            final var accountId = requireNonNull(key.accountId());
            recordBuilder.accountID(accountId);
            if (recordMemo != null) {
                recordBuilder.memo(recordMemo);
            }

            var txnBody = map.get(key);
            if (overrideAutoRenewPeriod != null) {
                txnBody.autoRenewPeriod(Duration.newBuilder().seconds(overrideAutoRenewPeriod));
            }
            var txnBuilder =
                    Transaction.newBuilder().body(TransactionBody.newBuilder().cryptoCreateAccount(txnBody));
            recordBuilder.transaction(txnBuilder.build());

            log.debug("Queued synthetic CryptoCreate for {} account {}", recordMemo, accountId);
        }
    }
}
