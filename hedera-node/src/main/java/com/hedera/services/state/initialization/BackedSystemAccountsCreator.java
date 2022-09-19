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
package com.hedera.services.state.initialization;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.context.properties.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_NUM_SYSTEM_ACCOUNTS;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.swirlds.common.system.address.AddressBook;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class BackedSystemAccountsCreator implements SystemAccountsCreator {
    private static final Logger log = LogManager.getLogger(BackedSystemAccountsCreator.class);

    public static final long FUNDING_ACCOUNT_EXPIRY = 33197904000L;

    private final AccountNumbers accountNums;
    private final PropertySource properties;
    private final Supplier<JEd25519Key> genesisKeySource;
    private final TreasuryCloner treasuryCloner;

    private JKey genesisKey;

    @Inject
    public BackedSystemAccountsCreator(
            final AccountNumbers accountNums,
            final @CompositeProps PropertySource properties,
            final Supplier<JEd25519Key> genesisKeySource,
            final TreasuryCloner treasuryCloner) {
        this.accountNums = accountNums;
        this.properties = properties;
        this.genesisKeySource = genesisKeySource;
        this.treasuryCloner = treasuryCloner;
    }

    /** {@inheritDoc} */
    @Override
    public void ensureSystemAccounts(
            final BackingStore<AccountID, MerkleAccount> accounts, final AddressBook addressBook) {
        long systemAccounts = properties.getIntProperty(LEDGER_NUM_SYSTEM_ACCOUNTS);
        long expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        long tinyBarFloat = properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT);

        for (long num = 1; num <= systemAccounts; num++) {
            var id = STATIC_PROPERTIES.scopedAccountWith(num);
            if (accounts.contains(id)) {
                continue;
            }
            if (num == accountNums.treasury()) {
                accounts.put(id, accountWith(tinyBarFloat, expiry));
            } else {
                accounts.put(id, accountWith(0, expiry));
            }
        }

        final var stakingRewardAccountNum = accountNums.stakingRewardAccount();
        final var stakingRewardAccountId =
                STATIC_PROPERTIES.scopedAccountWith(stakingRewardAccountNum);
        final var nodeRewardAccountNum = accountNums.nodeRewardAccount();
        final var nodeRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(nodeRewardAccountNum);
        final var stakingFundAccounts = List.of(stakingRewardAccountId, nodeRewardAccountId);
        for (final var id : stakingFundAccounts) {
            if (!accounts.contains(id)) {
                final var stakingFundAccount = new MerkleAccount();
                customizeAsStakingFund(stakingFundAccount);
                accounts.put(id, stakingFundAccount);
            }
        }
        for (long num = 900; num <= 1000; num++) {
            var id = STATIC_PROPERTIES.scopedAccountWith(num);
            if (!accounts.contains(id)) {
                accounts.put(id, accountWith(0, expiry));
            }
        }

        treasuryCloner.ensureTreasuryClonesExist();

        var ledgerFloat = 0L;
        final var allIds = accounts.idSet();
        for (final var id : allIds) {
            ledgerFloat += accounts.getImmutableRef(id).getBalance();
        }
        log.info("Ledger float is {} tinyBars in {} accounts.", ledgerFloat, allIds.size());
    }

    public static void customizeAsStakingFund(final MerkleAccount account) {
        account.setExpiry(FUNDING_ACCOUNT_EXPIRY);
        account.setAccountKey(EMPTY_KEY);
        account.setSmartContract(false);
        account.setReceiverSigRequired(false);
        account.setMaxAutomaticAssociations(0);
    }

    private MerkleAccount accountWith(long balance, long expiry) {
        var account =
                new HederaAccountCustomizer()
                        .isReceiverSigRequired(false)
                        .isDeleted(false)
                        .expiry(expiry)
                        .memo("")
                        .isSmartContract(false)
                        .key(getGenesisKey())
                        .autoRenewPeriod(expiry)
                        .customizing(new MerkleAccount());
        try {
            account.setBalance(balance);
        } catch (NegativeAccountBalanceException e) {
            throw new IllegalStateException(e);
        }
        return account;
    }

    private JKey getGenesisKey() {
        if (genesisKey == null) {
            // Traditionally the genesis key has been a key list, keep that way to avoid breaking
            // any clients
            genesisKey =
                    asFcKeyUnchecked(
                            Key.newBuilder()
                                    .setKeyList(
                                            KeyList.newBuilder()
                                                    .addKeys(
                                                            asKeyUnchecked(genesisKeySource.get())))
                                    .build());
        }
        return genesisKey;
    }
}
