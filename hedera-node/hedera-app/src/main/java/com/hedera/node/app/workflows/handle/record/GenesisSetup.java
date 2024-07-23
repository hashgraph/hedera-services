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
import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.spi.workflows.record.SingleTransactionStreamBuilder.transactionWith;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.impl.schemas.SyntheticAccountCreator;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.workflows.GenesisContext;
import com.hedera.node.app.spi.workflows.record.SingleTransactionStreamBuilder;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
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
public class GenesisSetup {
    private static final Logger log = LogManager.getLogger(GenesisSetup.class);
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

    private final FileServiceImpl fileService;
    private final SyntheticAccountCreator syntheticAccountCreator;

    /**
     * Constructs a new {@link GenesisSetup}.
     */
    @Inject
    public GenesisSetup(
            @NonNull final FileServiceImpl fileService,
            @NonNull final SyntheticAccountCreator syntheticAccountCreator) {
        this.fileService = requireNonNull(fileService);
        this.syntheticAccountCreator = requireNonNull(syntheticAccountCreator);
    }

    /**
     * Creates the system entities within the given dispatch.
     *
     * @param dispatch the genesis dispatch
     */
    public void createSystemEntities(@NonNull final Dispatch dispatch) {
        final var config = dispatch.config();
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        final var firstUserNum = config.getConfigData(HederaConfig.class).firstUserEntity();
        final var systemAdminId = AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(config.getConfigData(AccountsConfig.class).systemAdmin())
                .build();
        final var genesisContext = new GenesisContext() {
            @Override
            public void dispatchCreation(@NonNull final TransactionBody txBody, final long entityNum) {
                requireNonNull(txBody);
                if (entityNum >= firstUserNum) {
                    throw new IllegalArgumentException("Cannot create user entity at genesis");
                }
                final var controlledNum = dispatch.stack()
                        .getWritableStates(EntityIdService.NAME)
                        .<EntityNumber>getSingleton(ENTITY_ID_STATE_KEY);
                controlledNum.put(new EntityNumber(entityNum - 1));
                final var recordBuilder = dispatch.handleContext()
                        .dispatchPrecedingTransaction(
                                txBody, SingleTransactionStreamBuilder.class, key -> true, systemAdminId);
                if (recordBuilder.status() != SUCCESS) {
                    log.error(
                            "Failed to dispatch genesis transaction {} for entity {} - {}",
                            txBody,
                            entityNum,
                            recordBuilder.status());
                }
            }

            @NonNull
            @Override
            public Configuration configuration() {
                return dispatch.config();
            }

            @NonNull
            @Override
            public NetworkInfo networkInfo() {
                return dispatch.handleContext().networkInfo();
            }

            @NonNull
            @Override
            public Instant now() {
                return dispatch.consensusNow();
            }
        };
        fileService.createSystemEntities(genesisContext);
        // Before returning, reset the next entity number to be the first user entity
        final var controlledNum = dispatch.stack()
                .getWritableStates(EntityIdService.NAME)
                .<EntityNumber>getSingleton(ENTITY_ID_STATE_KEY);
        controlledNum.put(new EntityNumber(firstUserNum - 1));
        dispatch.stack().commitFullStack();
    }

    /**
     * Called only once, before handling the first transaction in network history. Externalizes
     * side effects of genesis setup done in
     * {@link com.swirlds.platform.system.SwirldState#init(Platform, PlatformState, InitTrigger, SoftwareVersion)}.
     * <p>
     * Should be removed once
     *
     * @throws NullPointerException if called more than once
     */
    public void externalizeInitSideEffects(@NonNull final TokenContext context) {
        final var firstConsensusTime = context.consensusTime();
        log.info("Doing genesis setup at {}", firstConsensusTime);
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
            // Since this is only called at genesis, the active savepoint's preceding record capacity will be
            // Integer.MAX_VALUE and this will never fail with MAX_CHILD_RECORDS_EXCEEDED (c.f., HandleWorkflow)
            final var recordBuilder = context.addPrecedingChildRecordBuilder(GenesisAccountRecordBuilder.class);
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
