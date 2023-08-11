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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.spi.HapiUtils.FUNDING_ACCOUNT_EXPIRY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitive.ProtoString;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.BootstrapConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {
    private static final int MAX_ACCOUNTS = 1024;
    private static final int MAX_TOKEN_RELS = 1042;
    private static final int MAX_MINTABLE_NFTS = 4096;
    private static final SemanticVersion GENESIS_VERSION = SemanticVersion.DEFAULT;

    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ALIASES_KEY = "ALIASES";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String PAYER_RECORDS_KEY = "PAYER_RECORDS";
    public static final String STAKING_INFO_KEY = "STAKING_INFOS";
    public static final String STAKING_NETWORK_REWARDS_KEY = "STAKING_NETWORK_REWARDS";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(tokenSchema());
    }

    private Schema tokenSchema() {
        // Everything on disk that can be
        return new Schema(GENESIS_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        StateDefinition.inMemory(TOKENS_KEY, TokenID.PROTOBUF, Token.PROTOBUF),
                        StateDefinition.onDisk(ACCOUNTS_KEY, AccountID.PROTOBUF, Account.PROTOBUF, MAX_ACCOUNTS),
                        StateDefinition.onDisk(ALIASES_KEY, ProtoString.PROTOBUF, AccountID.PROTOBUF, MAX_ACCOUNTS),
                        StateDefinition.onDisk(NFTS_KEY, NftID.PROTOBUF, Nft.PROTOBUF, MAX_MINTABLE_NFTS),
                        StateDefinition.onDisk(
                                TOKEN_RELS_KEY, EntityIDPair.PROTOBUF, TokenRelation.PROTOBUF, MAX_TOKEN_RELS),
                        StateDefinition.inMemory(STAKING_INFO_KEY, EntityNumber.PROTOBUF, StakingNodeInfo.PROTOBUF),
                        StateDefinition.singleton(STAKING_NETWORK_REWARDS_KEY, NetworkStakingRewards.PROTOBUF));
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                // TBD Verify this is correct. We need to preload all the special accounts
                final var accounts = ctx.newStates().get(ACCOUNTS_KEY);
                final var accountsConfig = ctx.configuration().getConfigData(AccountsConfig.class);
                final var bootstrapConfig = ctx.configuration().getConfigData(BootstrapConfig.class);
                final var ledgerConfig = ctx.configuration().getConfigData(LedgerConfig.class);
                final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
                final var superUserKeyBytes = bootstrapConfig.genesisPublicKey();
                if (superUserKeyBytes.length() != 32) {
                    throw new IllegalStateException("'" + superUserKeyBytes + "' is not a possible Ed25519 public key");
                }
                final var superUserKey =
                        Key.newBuilder().ed25519(superUserKeyBytes).build();

                final var numSystemAccounts = ledgerConfig.numSystemAccounts();
                final var expiry = bootstrapConfig.systemEntityExpiry();
                final var tinyBarFloat = ledgerConfig.totalTinyBarFloat();

                for (long num = 1; num <= numSystemAccounts; num++) {
                    final var id = AccountID.newBuilder()
                            .shardNum(hederaConfig.shard())
                            .realmNum(hederaConfig.realm())
                            .accountNum(num)
                            .build();

                    if (accounts.contains(id)) {
                        continue;
                    }

                    final var accountTinyBars = num == accountsConfig.treasury() ? tinyBarFloat : 0L;
                    assert accountTinyBars >= 0L : "Negative account balance!";

                    accounts.put(
                            id,
                            Account.newBuilder()
                                    .receiverSigRequired(false)
                                    .deleted(false)
                                    .expiry(expiry)
                                    .memo("")
                                    .smartContract(false)
                                    .key(superUserKey)
                                    .autoRenewSecs(expiry) // TODO is this right?
                                    .accountId(id)
                                    .tinybarBalance(accountTinyBars)
                                    .build());
                }
                addStakingAccounts(asAccount(800), accounts, FUNDING_ACCOUNT_EXPIRY);
                addStakingAccounts(asAccount(801), accounts, FUNDING_ACCOUNT_EXPIRY);

                updateNetworkRewards(ctx);
            }
        };
    }

    private void updateNetworkRewards(final MigrationContext ctx) {
        // Set genesis network rewards state
        final var networkRewardsState = ctx.newStates().getSingleton(STAKING_NETWORK_REWARDS_KEY);
        final var networkRewards = NetworkStakingRewards.newBuilder()
                .pendingRewards(0)
                .totalStakedRewardStart(0)
                .totalStakedStart(0)
                .stakingRewardsActivated(true)
                .build();
        networkRewardsState.put(networkRewards);
    }

    private void addStakingAccounts(
            final AccountID id, final WritableKVState<Object, Object> accounts, final long expiry) {
        accounts.put(
                id,
                Account.newBuilder()
                        .receiverSigRequired(false)
                        .deleted(false)
                        .memo("")
                        .smartContract(false)
                        .key(EMPTY_KEY_LIST)
                        .expiry(expiry)
                        .accountId(id)
                        .maxAutoAssociations(0)
                        .tinybarBalance(0)
                        .build());
    }
}
