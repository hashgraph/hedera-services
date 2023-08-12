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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.node.app.spi.key.KeyUtils;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class responsible for initializing all system-related accounts during node startup. There are four
 * types of accounts to initialize:
 * <ol>
 *     <li>System accounts</li>
 *     <li>Staking accounts</li>
 *     <li>Multi-use accounts</li>
 *     <li>Treasury clones</li>
 * </ol>
 */
public class SystemAccountsInitializer {
    private static final Logger log = LogManager.getLogger(SystemAccountsInitializer.class);

    private static final int ZERO_BALANCE = 0;

    private static final long NUM_RESERVED_SYSTEM_ENTITIES = 750L;
    private static final long FIRST_POST_SYSTEM_FILE_ENTITY = 200L;
    private static final long FIRST_RESERVED_SYSTEM_CONTRACT = 350L;
    private static final long LAST_RESERVED_SYSTEM_CONTRACT = 399L;

    /**
     * Creates all system-related accounts during node startup, specifically:
     * <ol>
     *     <li>System accounts 1–(configured numSystemAccounts)</li>
     *     <li>The configured staking rewards and node rewards accounts</li>
     *     <li>Multipurpose accounts 900–1000</li>
     *     <li>Treasury clone accounts from 200–750 (excluding system contracts 350–399)</li>
     * </ol>
     * @param ctx the migration context to use for record building
     */
    public void createSystemAccounts(@NonNull final MigrationContext ctx) {
        final var recordsKeeper = ctx.genesisRecordsBuilder();

        final var accounts = ctx.newStates().<AccountID, Account>get(TokenServiceImpl.ACCOUNTS_KEY);
        final var accountsConfig = ctx.configuration().getConfigData(AccountsConfig.class);
        final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
        final var ledgerConfig = ctx.configuration().getConfigData(LedgerConfig.class);
        final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
        final var superUserKey = superUserKey(bootstrapConfig);

        final var numSystemAccounts = ledgerConfig.numSystemAccounts();
        final var expiry = bootstrapConfig.systemEntityExpiry();
        final var tinyBarFloat = ledgerConfig.totalTinyBarFloat();

        var systemAcctLedgerBalanceTotal = 0L;
        // ---------- Create system accounts -------------------------
        final var systemAccts = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        for (long num = 1; num <= numSystemAccounts; num++) {
            final var id = asAccountId(num, hederaConfig);
            if (accounts.contains(id)) {
                continue;
            }

            final var accountTinyBars = num == accountsConfig.treasury() ? tinyBarFloat : ZERO_BALANCE;
            assert accountTinyBars >= 0L : "Negative account balance!";

            final var account = newBaseSystemAccount()
                    .accountId(id)
                    .key(superUserKey)
                    .expiry(expiry)
                    .autoRenewSecs(expiry)
                    .tinybarBalance(accountTinyBars)
                    .build();
            systemAccts.put(account, newCryptoCreate(account));
            accounts.put(id, account);

            systemAcctLedgerBalanceTotal += accountTinyBars;
        }
        recordsKeeper.systemAccounts(systemAccts);

        // ---------- Create staking fund accounts -------------------------
        final var stakingAccts = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        final var stakingRewardAccountId = asAccountId(accountsConfig.stakingRewardAccount(), hederaConfig);
        final var nodeRewardAccountId = asAccountId(accountsConfig.nodeRewardAccount(), hederaConfig);
        final var stakingFundAccounts = List.of(stakingRewardAccountId, nodeRewardAccountId);
        for (final var id : stakingFundAccounts) {
            if (!accounts.contains(id)) {
                final var stakingFundAccount = newBaseSystemAccount()
                        .accountId(id)
                        .key(EMPTY_KEY_LIST)
                        .expiry(expiry)
                        .tinybarBalance(0)
                        .maxAutoAssociations(0)
                        .build();

                stakingAccts.put(stakingFundAccount, newCryptoCreate(stakingFundAccount));
                accounts.put(id, stakingFundAccount);
                // no need to add to system ledger balance because these accounts are initialized with zero
            }
        }
        recordsKeeper.stakingAccounts(stakingAccts);

        // ---------- Create extra multi-use accounts -------------------------
        final var multiAccts = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        for (long num = 900; num <= 1000; num++) {
            final var id = asAccountId(num, hederaConfig);
            if (!accounts.contains(id)) {
                final var account = newBaseSystemAccount()
                        .accountId(id)
                        .key(superUserKey)
                        .tinybarBalance(ZERO_BALANCE)
                        .expiry(expiry)
                        .build();
                multiAccts.put(account, newCryptoCreate(account));
                accounts.put(id, account);
                // no need to add to system ledger balance because these accounts are initialized with zero
            }
        }
        recordsKeeper.multipurposeAccounts(multiAccts);

        // ---------- Create treasury clones -------------------------
        // Since version 0.28.6, all of these clone accounts should either all exist (on a restart) or all not exist
        // (starting from genesis)
        final Account treasury = accounts.get(asAccountId(accountsConfig.treasury(), hederaConfig));
        final var treasuryClones = new HashMap<Account, CryptoCreateTransactionBody.Builder>();
        for (final var num : nonContractSystemNums()) {
            final var nextCloneId = asAccountId(num, hederaConfig);
            if (accounts.contains(nextCloneId)) {
                continue;
            }

            final var nextClone = newBaseSystemAccount()
                    .accountId(nextCloneId)
                    .declineReward(treasury.declineReward())
                    .expiry(treasury.expiry())
                    .key(treasury.key())
                    .autoRenewSecs(treasury.autoRenewSecs())
                    .build();
            treasuryClones.put(nextClone, newCryptoCreate(nextClone));
            accounts.put(nextCloneId, nextClone);
        }
        recordsKeeper.treasuryClones(treasuryClones);
        log.info(
                "Created {} zero-balance accounts cloning treasury properties in the {}-{} range",
                treasuryClones.size(),
                FIRST_POST_SYSTEM_FILE_ENTITY,
                NUM_RESERVED_SYSTEM_ENTITIES);

        log.info(
                "Ledger float is {} tinyBars in {} accounts.",
                systemAcctLedgerBalanceTotal,
                accounts.modifiedKeys().size());
    }

    private static Key superUserKey(@NonNull final BootstrapConfig bootstrapConfig) {
        final var superUserKeyBytes = bootstrapConfig.genesisPublicKey();
        if (superUserKeyBytes.length() != KeyUtils.ED25519_BYTE_LENGTH) {
            throw new IllegalStateException("'" + superUserKeyBytes + "' is not a possible Ed25519 public key");
        }

        return Key.newBuilder().ed25519(superUserKeyBytes).build();
    }

    private static CryptoCreateTransactionBody.Builder newCryptoCreate(Account account) {
        return CryptoCreateTransactionBody.newBuilder()
                .key(account.key())
                .memo(account.memo())
                .declineReward(account.declineReward())
                .receiverSigRequired(account.receiverSigRequired())
                .autoRenewPeriod(
                        Duration.newBuilder().seconds(account.autoRenewSecs()).build())
                .initialBalance(account.tinybarBalance());
    }

    private static Account.Builder newBaseSystemAccount() {
        return Account.newBuilder()
                .receiverSigRequired(false)
                .deleted(false)
                .memo("")
                .smartContract(false);
    }

    private static AccountID asAccountId(final long acctNum, final HederaConfig hederaConfig) {
        return AccountID.newBuilder()
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .accountNum(acctNum)
                .build();
    }

    private static long[] nonContractSystemNums() {
        return LongStream.rangeClosed(FIRST_POST_SYSTEM_FILE_ENTITY, NUM_RESERVED_SYSTEM_ENTITIES)
                .filter(i -> i < FIRST_RESERVED_SYSTEM_CONTRACT || i > LAST_RESERVED_SYSTEM_CONTRACT)
                .toArray();
    }
}
