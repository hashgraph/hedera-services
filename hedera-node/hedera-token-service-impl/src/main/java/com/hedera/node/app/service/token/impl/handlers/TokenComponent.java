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

/**
 * Wrapper class for all handlers of the token service
 */
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

    /**
     * @param cryptoCreateHandler               the cryptoCreateHandler
     * @param cryptoUpdateHandler               the cryptoUpdateHandler
     * @param cryptoTransferHandler             the cryptoTransferHandler
     * @param cryptoDeleteHandler               the cryptoDeleteHandler
     * @param cryptoApproveAllowanceHandler     the cryptoApproveAllowanceHandler
     * @param cryptoDeleteAllowanceHandler      the cryptoDeleteAllowanceHandler
     * @param cryptoAddLiveHashHandler          the cryptoAddLiveHashHandler
     * @param cryptoDeleteLiveHashHandler       the cryptoDeleteLiveHashHandler
     * @param tokenCreateHandler                the tokenCreateHandler
     * @param tokenUpdateHandler                the tokenUpdateHandler
     * @param tokenMintHandler                  the tokenMintHandler
     * @param tokenBurnHandler                  the tokenBurnHandler
     * @param tokenDeleteHandler                the tokenDeleteHandler
     * @param tokenAccountWipeHandler           the tokenAccountWipeHandler
     * @param tokenFreezeAccountHandler         the tokenFreezeAccountHandler
     * @param tokenUnfreezeAccountHandler       the tokenUnfreezeAccountHandler
     * @param tokenGrantKycToAccountHandler     the tokenGrantKycToAccountHandler
     * @param tokenRevokeKycFromAccountHandler  the tokenRevokeKycFromAccountHandler
     * @param tokenAssociateToAccountHandler    the tokenAssociateToAccountHandler
     * @param tokenDissociateFromAccountHandler the tokenDissociateFromAccountHandler
     * @param tokenFeeScheduleUpdateHandler     the tokenFeeScheduleUpdateHandler
     * @param tokenPauseHandler                 the tokenPauseHandler
     * @param tokenUnpauseHandler               the tokenUnpauseHandler
     * @param cryptoGetAccountBalanceHandler    the cryptoGetAccountBalanceHandler
     * @param cryptoGetAccountInfoHandler       the cryptoGetAccountInfoHandler
     * @param cryptoGetAccountRecordsHandler    the cryptoGetAccountRecordsHandler
     * @param cryptoGetLiveHashHandler          the cryptoGetLiveHashHandler
     * @param cryptoGetStakersHandler           the cryptoGetStakersHandler
     * @param tokenGetInfoHandler               the tokenGetInfoHandler
     * @param tokenGetAccountNftInfosHandler    the tokenGetAccountNftInfosHandler
     * @param tokenGetNftInfoHandler            the tokenGetNftInfoHandler
     * @param tokenGetNftInfosHandler           the tokenGetNftInfosHandler
     */
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

    /**
     * Gets the cryptoCreateHandler.
     *
     * @return the cryptoCreateHandler
     */
    public CryptoCreateHandler getCryptoCreateHandler() {
        return cryptoCreateHandler;
    }

    /**
     * Gets the cryptoUpdateHandler.
     *
     * @return the cryptoUpdateHandler
     */
    public CryptoUpdateHandler getCryptoUpdateHandler() {
        return cryptoUpdateHandler;
    }

    /**
     * Gets the cryptoTransferHandler.
     *
     * @return the cryptoTransferHandler
     */
    public CryptoTransferHandler getCryptoTransferHandler() {
        return cryptoTransferHandler;
    }

    /**
     * Gets the cryptoDeleteHandler.
     *
     * @return the cryptoDeleteHandler
     */
    public CryptoDeleteHandler getCryptoDeleteHandler() {
        return cryptoDeleteHandler;
    }

    /**
     * Gets the cryptoApproveAllowanceHandler.
     *
     * @return the cryptoApproveAllowanceHandler
     */
    public CryptoApproveAllowanceHandler getCryptoApproveAllowanceHandler() {
        return cryptoApproveAllowanceHandler;
    }

    /**
     * Gets the cryptoDeleteAllowanceHandler.
     *
     * @return the cryptoDeleteAllowanceHandler
     */
    public CryptoDeleteAllowanceHandler getCryptoDeleteAllowanceHandler() {
        return cryptoDeleteAllowanceHandler;
    }

    /**
     * Gets the cryptoAddLiveHashHandler.
     *
     * @return the cryptoAddLiveHashHandler
     */
    public CryptoAddLiveHashHandler getCryptoAddLiveHashHandler() {
        return cryptoAddLiveHashHandler;
    }

    /**
     * Gets the cryptoDeleteLiveHashHandler.
     *
     * @return the cryptoDeleteLiveHashHandler
     */
    public CryptoDeleteLiveHashHandler getCryptoDeleteLiveHashHandler() {
        return cryptoDeleteLiveHashHandler;
    }

    /**
     * Gets the tokenCreateHandler.
     *
     * @return the tokenCreateHandler
     */
    public TokenCreateHandler getTokenCreateHandler() {
        return tokenCreateHandler;
    }

    /**
     * Gets the tokenUpdateHandler.
     *
     * @return the tokenUpdateHandler
     */
    public TokenUpdateHandler getTokenUpdateHandler() {
        return tokenUpdateHandler;
    }

    /**
     * Gets the tokenMintHandler.
     *
     * @return the tokenMintHandler
     */
    public TokenMintHandler getTokenMintHandler() {
        return tokenMintHandler;
    }

    /**
     * Gets the tokenBurnHandler.
     *
     * @return the tokenBurnHandler
     */
    public TokenBurnHandler getTokenBurnHandler() {
        return tokenBurnHandler;
    }

    /**
     * Gets the tokenDeleteHandler.
     *
     * @return the tokenDeleteHandler
     */
    public TokenDeleteHandler getTokenDeleteHandler() {
        return tokenDeleteHandler;
    }

    /**
     * Gets the tokenAccountWipeHandler.
     *
     * @return the tokenAccountWipeHandler
     */
    public TokenAccountWipeHandler getTokenAccountWipeHandler() {
        return tokenAccountWipeHandler;
    }

    /**
     * Gets the tokenFreezeAccountHandler.
     *
     * @return the tokenFreezeAccountHandler
     */
    public TokenFreezeAccountHandler getTokenFreezeAccountHandler() {
        return tokenFreezeAccountHandler;
    }

    /**
     * Gets the tokenUnfreezeAccountHandler.
     *
     * @return the tokenUnfreezeAccountHandler
     */
    public TokenUnfreezeAccountHandler getTokenUnfreezeAccountHandler() {
        return tokenUnfreezeAccountHandler;
    }

    /**
     * Gets the tokenGrantKycToAccountHandler.
     *
     * @return the tokenGrantKycToAccountHandler
     */
    public TokenGrantKycToAccountHandler getTokenGrantKycToAccountHandler() {
        return tokenGrantKycToAccountHandler;
    }

    /**
     * Gets the tokenRevokeKycFromAccountHandler.
     *
     * @return the tokenRevokeKycFromAccountHandler
     */
    public TokenRevokeKycFromAccountHandler getTokenRevokeKycFromAccountHandler() {
        return tokenRevokeKycFromAccountHandler;
    }

    /**
     * Gets the tokenAssociateToAccountHandler.
     *
     * @return the tokenAssociateToAccountHandler
     */
    public TokenAssociateToAccountHandler getTokenAssociateToAccountHandler() {
        return tokenAssociateToAccountHandler;
    }

    /**
     * Gets the tokenDissociateFromAccountHandler.
     *
     * @return the tokenDissociateFromAccountHandler
     */
    public TokenDissociateFromAccountHandler getTokenDissociateFromAccountHandler() {
        return tokenDissociateFromAccountHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public TokenFeeScheduleUpdateHandler getTokenFeeScheduleUpdateHandler() {
        return tokenFeeScheduleUpdateHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public TokenPauseHandler getTokenPauseHandler() {
        return tokenPauseHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public TokenUnpauseHandler getTokenUnpauseHandler() {
        return tokenUnpauseHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public CryptoGetAccountBalanceHandler getCryptoGetAccountBalanceHandler() {
        return cryptoGetAccountBalanceHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetAccountInfoHandler getCryptoGetAccountInfoHandler() {
        return cryptoGetAccountInfoHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetAccountRecordsHandler getCryptoGetAccountRecordsHandler() {
        return cryptoGetAccountRecordsHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetLiveHashHandler getCryptoGetLiveHashHandler() {
        return cryptoGetLiveHashHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetStakersHandler getCryptoGetStakersHandler() {
        return cryptoGetStakersHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetInfoHandler getTokenGetInfoHandler() {
        return tokenGetInfoHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetAccountNftInfosHandler getTokenGetAccountNftInfosHandler() {
        return tokenGetAccountNftInfosHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetNftInfoHandler getTokenGetNftInfoHandler() {
        return tokenGetNftInfoHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetNftInfosHandler getTokenGetNftInfosHandler() {
        return tokenGetNftInfosHandler;
    }
}
