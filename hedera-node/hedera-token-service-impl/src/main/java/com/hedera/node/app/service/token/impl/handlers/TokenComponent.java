/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenComponent {

    private final CryptoCreateHandler cryptoCreateHandler;
    private final CryptoUpdateHandler cryptoUpdateHandler;
    private final CryptoTransferHandler cryptoTransferHandler;
    private final CryptoDeleteHandler cryptoDeleteHandler;
    private final CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;
    private final CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;
    private final CryptoAddLiveHashHandler cryptoAddLiveHashHandler;
    private final CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;
    private final TokenCreateHandler tokenCreateHandler;
    private final TokenUpdateHandler tokenUpdateHandler;
    private final TokenMintHandler tokenMintHandler;
    private final TokenBurnHandler tokenBurnHandler;
    private final TokenDeleteHandler tokenDeleteHandler;
    private final TokenAccountWipeHandler tokenAccountWipeHandler;
    private final TokenFreezeAccountHandler tokenFreezeAccountHandler;
    private final TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;
    private final TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;
    private final TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;
    private final TokenAssociateToAccountHandler tokenAssociateToAccountHandler;
    private final TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;
    private final TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;
    private final TokenPauseHandler tokenPauseHandler;
    private final TokenUnpauseHandler tokenUnpauseHandler;
    private final CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler;
    private final CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler;
    private final CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler;
    private final CryptoGetLiveHashHandler cryptoGetLiveHashHandler;
    private final CryptoGetStakersHandler cryptoGetStakersHandler;
    private final TokenGetInfoHandler tokenGetInfoHandler;
    private final TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler;
    private final TokenGetNftInfoHandler tokenGetNftInfoHandler;
    private final TokenGetNftInfosHandler tokenGetNftInfosHandler;

    @Inject
    public TokenComponent(@NonNull final CryptoCreateHandler cryptoCreateHandler,
            @NonNull final CryptoUpdateHandler cryptoUpdateHandler,
            @NonNull final CryptoTransferHandler cryptoTransferHandler,
            @NonNull final CryptoDeleteHandler cryptoDeleteHandler,
            @NonNull final CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler,
            @NonNull final CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler,
            @NonNull final CryptoAddLiveHashHandler cryptoAddLiveHashHandler,
            @NonNull final CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler,
            @NonNull final TokenCreateHandler tokenCreateHandler, @NonNull final TokenUpdateHandler tokenUpdateHandler,
            @NonNull final TokenMintHandler tokenMintHandler, @NonNull final TokenBurnHandler tokenBurnHandler,
            @NonNull final TokenDeleteHandler tokenDeleteHandler,
            @NonNull final TokenAccountWipeHandler tokenAccountWipeHandler,
            @NonNull final TokenFreezeAccountHandler tokenFreezeAccountHandler,
            @NonNull final TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler,
            @NonNull final TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler,
            @NonNull final TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler,
            @NonNull final TokenAssociateToAccountHandler tokenAssociateToAccountHandler,
            @NonNull final TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler,
            @NonNull final TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler,
            @NonNull final TokenPauseHandler tokenPauseHandler, @NonNull final TokenUnpauseHandler tokenUnpauseHandler,
            @NonNull final CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler,
            @NonNull final CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler,
            @NonNull final CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler,
            @NonNull final CryptoGetLiveHashHandler cryptoGetLiveHashHandler,
            @NonNull final CryptoGetStakersHandler cryptoGetStakersHandler,
            @NonNull final TokenGetInfoHandler tokenGetInfoHandler,
            @NonNull final TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler,
            @NonNull final TokenGetNftInfoHandler tokenGetNftInfoHandler,
            @NonNull final TokenGetNftInfosHandler tokenGetNftInfosHandler) {
        this.cryptoCreateHandler = cryptoCreateHandler;
        this.cryptoUpdateHandler = cryptoUpdateHandler;
        this.cryptoTransferHandler = cryptoTransferHandler;
        this.cryptoDeleteHandler = cryptoDeleteHandler;
        this.cryptoApproveAllowanceHandler = cryptoApproveAllowanceHandler;
        this.cryptoDeleteAllowanceHandler = cryptoDeleteAllowanceHandler;
        this.cryptoAddLiveHashHandler = cryptoAddLiveHashHandler;
        this.cryptoDeleteLiveHashHandler = cryptoDeleteLiveHashHandler;
        this.tokenCreateHandler = tokenCreateHandler;
        this.tokenUpdateHandler = tokenUpdateHandler;
        this.tokenMintHandler = tokenMintHandler;
        this.tokenBurnHandler = tokenBurnHandler;
        this.tokenDeleteHandler = tokenDeleteHandler;
        this.tokenAccountWipeHandler = tokenAccountWipeHandler;
        this.tokenFreezeAccountHandler = tokenFreezeAccountHandler;
        this.tokenUnfreezeAccountHandler = tokenUnfreezeAccountHandler;
        this.tokenGrantKycToAccountHandler = tokenGrantKycToAccountHandler;
        this.tokenRevokeKycFromAccountHandler = tokenRevokeKycFromAccountHandler;
        this.tokenAssociateToAccountHandler = tokenAssociateToAccountHandler;
        this.tokenDissociateFromAccountHandler = tokenDissociateFromAccountHandler;
        this.tokenFeeScheduleUpdateHandler = tokenFeeScheduleUpdateHandler;
        this.tokenPauseHandler = tokenPauseHandler;
        this.tokenUnpauseHandler = tokenUnpauseHandler;
        this.cryptoGetAccountBalanceHandler = cryptoGetAccountBalanceHandler;
        this.cryptoGetAccountInfoHandler = cryptoGetAccountInfoHandler;
        this.cryptoGetAccountRecordsHandler = cryptoGetAccountRecordsHandler;
        this.cryptoGetLiveHashHandler = cryptoGetLiveHashHandler;
        this.cryptoGetStakersHandler = cryptoGetStakersHandler;
        this.tokenGetInfoHandler = tokenGetInfoHandler;
        this.tokenGetAccountNftInfosHandler = tokenGetAccountNftInfosHandler;
        this.tokenGetNftInfoHandler = tokenGetNftInfoHandler;
        this.tokenGetNftInfosHandler = tokenGetNftInfosHandler;
    }

    public CryptoCreateHandler getCryptoCreateHandler() {
        return cryptoCreateHandler;
    }

    public CryptoUpdateHandler getCryptoUpdateHandler() {
        return cryptoUpdateHandler;
    }

    public CryptoTransferHandler getCryptoTransferHandler() {
        return cryptoTransferHandler;
    }

    public CryptoDeleteHandler getCryptoDeleteHandler() {
        return cryptoDeleteHandler;
    }

    public CryptoApproveAllowanceHandler getCryptoApproveAllowanceHandler() {
        return cryptoApproveAllowanceHandler;
    }

    public CryptoDeleteAllowanceHandler getCryptoDeleteAllowanceHandler() {
        return cryptoDeleteAllowanceHandler;
    }

    public CryptoAddLiveHashHandler getCryptoAddLiveHashHandler() {
        return cryptoAddLiveHashHandler;
    }

    public CryptoDeleteLiveHashHandler getCryptoDeleteLiveHashHandler() {
        return cryptoDeleteLiveHashHandler;
    }

    public TokenCreateHandler getTokenCreateHandler() {
        return tokenCreateHandler;
    }

    public TokenUpdateHandler getTokenUpdateHandler() {
        return tokenUpdateHandler;
    }

    public TokenMintHandler getTokenMintHandler() {
        return tokenMintHandler;
    }

    public TokenBurnHandler getTokenBurnHandler() {
        return tokenBurnHandler;
    }

    public TokenDeleteHandler getTokenDeleteHandler() {
        return tokenDeleteHandler;
    }

    public TokenAccountWipeHandler getTokenAccountWipeHandler() {
        return tokenAccountWipeHandler;
    }

    public TokenFreezeAccountHandler getTokenFreezeAccountHandler() {
        return tokenFreezeAccountHandler;
    }

    public TokenUnfreezeAccountHandler getTokenUnfreezeAccountHandler() {
        return tokenUnfreezeAccountHandler;
    }

    public TokenGrantKycToAccountHandler getTokenGrantKycToAccountHandler() {
        return tokenGrantKycToAccountHandler;
    }

    public TokenRevokeKycFromAccountHandler getTokenRevokeKycFromAccountHandler() {
        return tokenRevokeKycFromAccountHandler;
    }

    public TokenAssociateToAccountHandler getTokenAssociateToAccountHandler() {
        return tokenAssociateToAccountHandler;
    }

    public TokenDissociateFromAccountHandler getTokenDissociateFromAccountHandler() {
        return tokenDissociateFromAccountHandler;
    }

    public TokenFeeScheduleUpdateHandler getTokenFeeScheduleUpdateHandler() {
        return tokenFeeScheduleUpdateHandler;
    }

    public TokenPauseHandler getTokenPauseHandler() {
        return tokenPauseHandler;
    }

    public TokenUnpauseHandler getTokenUnpauseHandler() {
        return tokenUnpauseHandler;
    }

    public CryptoGetAccountBalanceHandler getCryptoGetAccountBalanceHandler() {
        return cryptoGetAccountBalanceHandler;
    }

    public CryptoGetAccountInfoHandler getCryptoGetAccountInfoHandler() {
        return cryptoGetAccountInfoHandler;
    }

    public CryptoGetAccountRecordsHandler getCryptoGetAccountRecordsHandler() {
        return cryptoGetAccountRecordsHandler;
    }

    public CryptoGetLiveHashHandler getCryptoGetLiveHashHandler() {
        return cryptoGetLiveHashHandler;
    }

    public CryptoGetStakersHandler getCryptoGetStakersHandler() {
        return cryptoGetStakersHandler;
    }

    public TokenGetInfoHandler getTokenGetInfoHandler() {
        return tokenGetInfoHandler;
    }

    public TokenGetAccountNftInfosHandler getTokenGetAccountNftInfosHandler() {
        return tokenGetAccountNftInfosHandler;
    }

    public TokenGetNftInfoHandler getTokenGetNftInfoHandler() {
        return tokenGetNftInfoHandler;
    }

    public TokenGetNftInfosHandler getTokenGetNftInfosHandler() {
        return tokenGetNftInfosHandler;
    }
}
