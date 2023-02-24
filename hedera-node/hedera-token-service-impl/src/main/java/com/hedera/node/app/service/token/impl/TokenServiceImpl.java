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

import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.handlers.CryptoAddLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountBalanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetAccountRecordsHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoGetStakersHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetAccountNftInfosHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetNftInfoHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGetNftInfosHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.serdes.EntityNumSerdes;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {

    private static final int MAX_ACCOUNTS = 1024;
    private static final int MAX_TOKEN_RELS = 1042;
    private static final int MAX_MINTABLE_NFTS = 4096;
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    public static final String NFTS_KEY = "NFTS";
    public static final String TOKENS_KEY = "TOKENS";
    public static final String ACCOUNTS_KEY = "ACCOUNTS";
    public static final String TOKEN_RELS_KEY = "TOKEN_RELS";
    public static final String PAYER_RECORDS_KEY = "PAYER_RECORDS";

    private final TokenAccountWipeHandler tokenAccountWipeHandler;

    private final TokenAssociateToAccountHandler tokenAssociateToAccountHandler;

    private final TokenBurnHandler tokenBurnHandler;

    private final TokenCreateHandler tokenCreateHandler;

    private final TokenDeleteHandler tokenDeleteHandler;

    private final TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;

    private final TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;

    private final TokenFreezeAccountHandler tokenFreezeAccountHandler;

    private final TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler;

    private final TokenGetInfoHandler tokenGetInfoHandler;

    private final TokenGetNftInfoHandler tokenGetNftInfoHandler;

    private final TokenGetNftInfosHandler tokenGetNftInfosHandler;

    private final TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;

    private final TokenMintHandler tokenMintHandler;

    private final TokenPauseHandler tokenPauseHandler;

    private final TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;

    private final TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;

    private final TokenUnpauseHandler tokenUnpauseHandler;

    private final CryptoAddLiveHashHandler cryptoAddLiveHashHandler;

    private final CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;

    private final CryptoCreateHandler cryptoCreateHandler;

    private final CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;

    private final CryptoDeleteHandler cryptoDeleteHandler;

    private final CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    private final CryptoTransferHandler cryptoTransferHandler;

    private final CryptoUpdateHandler cryptoUpdateHandler;

    private final CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler;

    private final CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler;

    private final CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler;

    private final CryptoGetLiveHashHandler cryptoGetLiveHashHandler;

    private final CryptoGetStakersHandler cryptoGetStakersHandler;

    /**
     * Constructs a {@link TokenServiceImpl} instance.
     */
    public TokenServiceImpl() {
        this.tokenAccountWipeHandler = new TokenAccountWipeHandler();
        this.tokenAssociateToAccountHandler = new TokenAssociateToAccountHandler();
        this.tokenBurnHandler = new TokenBurnHandler();
        this.tokenCreateHandler = new TokenCreateHandler();
        this.tokenDeleteHandler = new TokenDeleteHandler();
        this.tokenDissociateFromAccountHandler = new TokenDissociateFromAccountHandler();
        this.tokenFeeScheduleUpdateHandler = new TokenFeeScheduleUpdateHandler();
        this.tokenFreezeAccountHandler = new TokenFreezeAccountHandler();
        this.tokenGetAccountNftInfosHandler = new TokenGetAccountNftInfosHandler();
        this.tokenGetInfoHandler = new TokenGetInfoHandler();
        this.tokenGetNftInfoHandler = new TokenGetNftInfoHandler();
        this.tokenGetNftInfosHandler = new TokenGetNftInfosHandler();
        this.tokenGrantKycToAccountHandler = new TokenGrantKycToAccountHandler();
        this.tokenMintHandler = new TokenMintHandler();
        this.tokenPauseHandler = new TokenPauseHandler();
        this.tokenRevokeKycFromAccountHandler = new TokenRevokeKycFromAccountHandler();
        this.tokenUnfreezeAccountHandler = new TokenUnfreezeAccountHandler();
        this.tokenUnpauseHandler = new TokenUnpauseHandler();
        this.cryptoAddLiveHashHandler = new CryptoAddLiveHashHandler();
        this.cryptoApproveAllowanceHandler = new CryptoApproveAllowanceHandler();
        this.cryptoCreateHandler = new CryptoCreateHandler();
        this.cryptoDeleteAllowanceHandler = new CryptoDeleteAllowanceHandler();
        this.cryptoDeleteHandler = new CryptoDeleteHandler();
        this.cryptoDeleteLiveHashHandler = new CryptoDeleteLiveHashHandler();
        this.cryptoTransferHandler = new CryptoTransferHandler();
        this.cryptoUpdateHandler = new CryptoUpdateHandler();
        this.cryptoGetAccountBalanceHandler = new CryptoGetAccountBalanceHandler();
        this.cryptoGetAccountInfoHandler = new CryptoGetAccountInfoHandler();
        this.cryptoGetAccountRecordsHandler = new CryptoGetAccountRecordsHandler();
        this.cryptoGetLiveHashHandler = new CryptoGetLiveHashHandler();
        this.cryptoGetStakersHandler = new CryptoGetStakersHandler();
    }

    /**
     * Returns the {@link TokenAccountWipeHandler} instance.
     *
     * @return the {@link TokenAccountWipeHandler} instance
     */
    @NonNull
    public TokenAccountWipeHandler getTokenAccountWipeHandler() {
        return tokenAccountWipeHandler;
    }

    /**
     * Returns the {@link TokenAssociateToAccountHandler} instance.
     *
     * @return the {@link TokenAssociateToAccountHandler} instance
     */
    @NonNull
    public TokenAssociateToAccountHandler getTokenAssociateToAccountHandler() {
        return tokenAssociateToAccountHandler;
    }

    /**
     * Returns the {@link TokenBurnHandler} instance.
     *
     * @return the {@link TokenBurnHandler} instance
     */
    @NonNull
    public TokenBurnHandler getTokenBurnHandler() {
        return tokenBurnHandler;
    }

    /**
     * Returns the {@link TokenCreateHandler} instance.
     *
     * @return the {@link TokenCreateHandler} instance
     */
    @NonNull
    public TokenCreateHandler getTokenCreateHandler() {
        return tokenCreateHandler;
    }

    /**
     * Returns the {@link TokenDeleteHandler} instance.
     *
     * @return the {@link TokenDeleteHandler} instance
     */
    @NonNull
    public TokenDeleteHandler getTokenDeleteHandler() {
        return tokenDeleteHandler;
    }

    /**
     * Returns the {@link TokenDissociateFromAccountHandler} instance.
     *
     * @return the {@link TokenDissociateFromAccountHandler} instance
     */
    @NonNull
    public TokenDissociateFromAccountHandler getTokenDissociateFromAccountHandler() {
        return tokenDissociateFromAccountHandler;
    }

    /**
     * Returns the {@link TokenFeeScheduleUpdateHandler} instance.
     *
     * @return the {@link TokenFeeScheduleUpdateHandler} instance
     */
    @NonNull
    public TokenFeeScheduleUpdateHandler getTokenFeeScheduleUpdateHandler() {
        return tokenFeeScheduleUpdateHandler;
    }

    /**
     * Returns the {@link TokenFreezeAccountHandler} instance.
     *
     * @return the {@link TokenFreezeAccountHandler} instance
     */
    @NonNull
    public TokenFreezeAccountHandler getTokenFreezeAccountHandler() {
        return tokenFreezeAccountHandler;
    }

    /**
     * Returns the {@link TokenGetAccountNftInfosHandler} instance.
     *
     * @return the {@link TokenGetAccountNftInfosHandler} instance
     */
    @NonNull
    public TokenGetAccountNftInfosHandler getTokenGetAccountNftInfosHandler() {
        return tokenGetAccountNftInfosHandler;
    }

    /**
     * Returns the {@link TokenGetInfoHandler} instance.
     *
     * @return the {@link TokenGetInfoHandler} instance
     */
    @NonNull
    public TokenGetInfoHandler getTokenGetInfoHandler() {
        return tokenGetInfoHandler;
    }

    /**
     * Returns the {@link TokenGetNftInfoHandler} instance.
     *
     * @return the {@link TokenGetNftInfoHandler} instance
     */
    @NonNull
    public TokenGetNftInfoHandler getTokenGetNftInfoHandler() {
        return tokenGetNftInfoHandler;
    }

    /**
     * Returns the {@link TokenGetNftInfosHandler} instance.
     *
     * @return the {@link TokenGetNftInfosHandler} instance
     */
    @NonNull
    public TokenGetNftInfosHandler getTokenGetNftInfosHandler() {
        return tokenGetNftInfosHandler;
    }

    /**
     * Returns the {@link TokenGrantKycToAccountHandler} instance.
     *
     * @return the {@link TokenGrantKycToAccountHandler} instance
     */
    @NonNull
    public TokenGrantKycToAccountHandler getTokenGrantKycToAccountHandler() {
        return tokenGrantKycToAccountHandler;
    }

    /**
     * Returns the {@link TokenMintHandler} instance.
     *
     * @return the {@link TokenMintHandler} instance
     */
    @NonNull
    public TokenMintHandler getTokenMintHandler() {
        return tokenMintHandler;
    }

    /**
     * Returns the {@link TokenPauseHandler} instance.
     *
     * @return the {@link TokenPauseHandler} instance
     */
    @NonNull
    public TokenPauseHandler getTokenPauseHandler() {
        return tokenPauseHandler;
    }

    /**
     * Returns the {@link TokenRevokeKycFromAccountHandler} instance.
     *
     * @return the {@link TokenRevokeKycFromAccountHandler} instance
     */
    @NonNull
    public TokenRevokeKycFromAccountHandler getTokenRevokeKycFromAccountHandler() {
        return tokenRevokeKycFromAccountHandler;
    }

    /**
     * Returns the {@link TokenUnfreezeAccountHandler} instance.
     *
     * @return the {@link TokenUnfreezeAccountHandler} instance
     */
    @NonNull
    public TokenUnfreezeAccountHandler getTokenUnfreezeAccountHandler() {
        return tokenUnfreezeAccountHandler;
    }

    /**
     * Returns the {@link TokenUnpauseHandler} instance.
     *
     * @return the {@link TokenUnpauseHandler} instance
     */
    @NonNull
    public TokenUnpauseHandler getTokenUnpauseHandler() {
        return tokenUnpauseHandler;
    }

    /**
     * Returns the {@link CryptoAddLiveHashHandler} instance.
     *
     * @return the {@link CryptoAddLiveHashHandler} instance
     */
    @NonNull
    public CryptoAddLiveHashHandler getCryptoAddLiveHashHandler() {
        return cryptoAddLiveHashHandler;
    }

    /**
     * Returns the {@link CryptoApproveAllowanceHandler} instance.
     *
     * @return the {@link CryptoApproveAllowanceHandler} instance
     */
    @NonNull
    public CryptoApproveAllowanceHandler getCryptoApproveAllowanceHandler() {
        return cryptoApproveAllowanceHandler;
    }

    /**
     * Returns the {@link CryptoCreateHandler} instance.
     *
     * @return the {@link CryptoCreateHandler} instance
     */
    @NonNull
    public CryptoCreateHandler getCryptoCreateHandler() {
        return cryptoCreateHandler;
    }

    /**
     * Returns the {@link CryptoDeleteAllowanceHandler} instance.
     *
     * @return the {@link CryptoDeleteAllowanceHandler} instance
     */
    @NonNull
    public CryptoDeleteAllowanceHandler getCryptoDeleteAllowanceHandler() {
        return cryptoDeleteAllowanceHandler;
    }

    /**
     * Returns the {@link CryptoDeleteHandler} instance.
     *
     * @return the {@link CryptoDeleteHandler} instance
     */
    @NonNull
    public CryptoDeleteHandler getCryptoDeleteHandler() {
        return cryptoDeleteHandler;
    }

    /**
     * Returns the {@link CryptoDeleteLiveHashHandler} instance.
     *
     * @return the {@link CryptoDeleteLiveHashHandler} instance
     */
    @NonNull
    public CryptoDeleteLiveHashHandler getCryptoDeleteLiveHashHandler() {
        return cryptoDeleteLiveHashHandler;
    }

    /**
     * Returns the {@link CryptoTransferHandler} instance.
     *
     * @return the {@link CryptoTransferHandler} instance
     */
    @NonNull
    public CryptoTransferHandler getCryptoTransferHandler() {
        return cryptoTransferHandler;
    }

    /**
     * Returns the {@link CryptoUpdateHandler} instance.
     *
     * @return the {@link CryptoUpdateHandler} instance
     */
    @NonNull
    public CryptoUpdateHandler getCryptoUpdateHandler() {
        return cryptoUpdateHandler;
    }

    /**
     * Returns the {@link CryptoGetAccountBalanceHandler} instance.
     *
     * @return the {@link CryptoGetAccountBalanceHandler} instance
     */
    @NonNull
    public CryptoGetAccountBalanceHandler getCryptoGetAccountBalanceHandler() {
        return cryptoGetAccountBalanceHandler;
    }

    /**
     * Returns the {@link CryptoGetAccountInfoHandler} instance.
     *
     * @return the {@link CryptoGetAccountInfoHandler} instance
     */
    @NonNull
    public CryptoGetAccountInfoHandler getCryptoGetAccountInfoHandler() {
        return cryptoGetAccountInfoHandler;
    }

    /**
     * Returns the {@link CryptoGetAccountRecordsHandler} instance.
     *
     * @return the {@link CryptoGetAccountRecordsHandler} instance
     */
    @NonNull
    public CryptoGetAccountRecordsHandler getCryptoGetAccountRecordsHandler() {
        return cryptoGetAccountRecordsHandler;
    }

    /**
     * Returns the {@link CryptoGetLiveHashHandler} instance.
     *
     * @return the {@link CryptoGetLiveHashHandler} instance
     */
    @NonNull
    public CryptoGetLiveHashHandler getCryptoGetLiveHashHandler() {
        return cryptoGetLiveHashHandler;
    }

    /**
     * Returns the {@link CryptoGetStakersHandler} instance.
     *
     * @return the {@link CryptoGetStakersHandler} instance
     */
    @NonNull
    public CryptoGetStakersHandler getCryptoGetStakersHandler() {
        return cryptoGetStakersHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandlers() {
        return Set.of(
                tokenAccountWipeHandler,
                tokenAssociateToAccountHandler,
                tokenBurnHandler,
                tokenCreateHandler,
                tokenDeleteHandler,
                tokenDissociateFromAccountHandler,
                tokenFeeScheduleUpdateHandler,
                tokenFreezeAccountHandler,
                tokenGrantKycToAccountHandler,
                tokenMintHandler,
                tokenPauseHandler,
                tokenRevokeKycFromAccountHandler,
                tokenUnfreezeAccountHandler,
                tokenUnpauseHandler,
                cryptoAddLiveHashHandler,
                cryptoApproveAllowanceHandler,
                cryptoCreateHandler,
                cryptoDeleteAllowanceHandler,
                cryptoDeleteHandler,
                cryptoDeleteLiveHashHandler,
                cryptoTransferHandler,
                cryptoUpdateHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandlers() {
        return Set.of(
                tokenGetAccountNftInfosHandler,
                tokenGetInfoHandler,
                tokenGetNftInfoHandler,
                tokenGetNftInfosHandler,
                cryptoGetAccountBalanceHandler,
                cryptoGetAccountInfoHandler,
                cryptoGetAccountRecordsHandler,
                cryptoGetLiveHashHandler,
                cryptoGetStakersHandler);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null").register(tokenSchema());
    }

    private Schema tokenSchema() {
        // Everything on disk that can be
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        tokensDef(), onDiskAccountsDef(), onDiskNftsDef(), onDiskTokenRelsDef(), payerRecordsDef());
            }
        };
    }

    private StateDefinition<EntityNumVirtualKey, OnDiskAccount> onDiskAccountsDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                EntityNumVirtualKey.CURRENT_VERSION, EntityNumVirtualKey::new, new EntityNumVirtualKeySerializer());
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForVirtualValue(OnDiskAccount.CURRENT_VERSION, OnDiskAccount::new);
        return StateDefinition.onDisk(ACCOUNTS_KEY, keySerdes, valueSerdes, MAX_ACCOUNTS);
    }

    private StateDefinition<EntityNum, MerklePayerRecords> payerRecordsDef() {
        final var keySerdes = new EntityNumSerdes();
        final var valueSerdes = MonoMapSerdesAdapter.serdesForSelfSerializable(
                MerklePayerRecords.CURRENT_VERSION, MerklePayerRecords::new);
        return StateDefinition.inMemory(PAYER_RECORDS_KEY, keySerdes, valueSerdes);
    }

    private StateDefinition<EntityNum, MerkleToken> tokensDef() {
        final var keySerdes = new EntityNumSerdes();
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForSelfSerializable(MerkleToken.CURRENT_VERSION, MerkleToken::new);
        return StateDefinition.inMemory(TOKENS_KEY, keySerdes, valueSerdes);
    }

    private StateDefinition<EntityNumVirtualKey, OnDiskTokenRel> onDiskTokenRelsDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                EntityNumVirtualKey.CURRENT_VERSION, EntityNumVirtualKey::new, new EntityNumVirtualKeySerializer());
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForVirtualValue(OnDiskTokenRel.CURRENT_VERSION, OnDiskTokenRel::new);
        return StateDefinition.onDisk(TOKEN_RELS_KEY, keySerdes, valueSerdes, MAX_TOKEN_RELS);
    }

    private StateDefinition<UniqueTokenKey, UniqueTokenValue> onDiskNftsDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                UniqueTokenKey.CURRENT_VERSION, UniqueTokenKey::new, new UniqueTokenKeySerializer());
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForVirtualValue(UniqueTokenValue.CURRENT_VERSION, UniqueTokenValue::new);
        return StateDefinition.onDisk(NFTS_KEY, keySerdes, valueSerdes, MAX_MINTABLE_NFTS);
    }
}
