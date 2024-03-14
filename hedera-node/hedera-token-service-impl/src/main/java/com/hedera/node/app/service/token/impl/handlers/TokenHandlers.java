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

package com.hedera.node.app.service.token.impl.handlers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Wrapper class for all handlers of the token service. This should be a {@code record} but it looks like Dagger does
 * not support Java records
 */
@Singleton
// Suppressing the warning that this class is too big
@SuppressWarnings("java:S6539")
public class TokenHandlers {

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
    private final TokenUpdateNftsHandler tokenUpdateNftsHandler;

    /**
     * Constructor for the TokenHandlers class
     */
    @Inject
    public TokenHandlers(
            @NonNull final CryptoCreateHandler cryptoCreateHandler,
            @NonNull final CryptoUpdateHandler cryptoUpdateHandler,
            @NonNull final CryptoTransferHandler cryptoTransferHandler,
            @NonNull final CryptoDeleteHandler cryptoDeleteHandler,
            @NonNull final CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler,
            @NonNull final CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler,
            @NonNull final CryptoAddLiveHashHandler cryptoAddLiveHashHandler,
            @NonNull final CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler,
            @NonNull final TokenCreateHandler tokenCreateHandler,
            @NonNull final TokenUpdateHandler tokenUpdateHandler,
            @NonNull final TokenMintHandler tokenMintHandler,
            @NonNull final TokenBurnHandler tokenBurnHandler,
            @NonNull final TokenDeleteHandler tokenDeleteHandler,
            @NonNull final TokenAccountWipeHandler tokenAccountWipeHandler,
            @NonNull final TokenFreezeAccountHandler tokenFreezeAccountHandler,
            @NonNull final TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler,
            @NonNull final TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler,
            @NonNull final TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler,
            @NonNull final TokenAssociateToAccountHandler tokenAssociateToAccountHandler,
            @NonNull final TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler,
            @NonNull final TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler,
            @NonNull final TokenPauseHandler tokenPauseHandler,
            @NonNull final TokenUnpauseHandler tokenUnpauseHandler,
            @NonNull final CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler,
            @NonNull final CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler,
            @NonNull final CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler,
            @NonNull final CryptoGetLiveHashHandler cryptoGetLiveHashHandler,
            @NonNull final CryptoGetStakersHandler cryptoGetStakersHandler,
            @NonNull final TokenGetInfoHandler tokenGetInfoHandler,
            @NonNull final TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler,
            @NonNull final TokenGetNftInfoHandler tokenGetNftInfoHandler,
            @NonNull final TokenGetNftInfosHandler tokenGetNftInfosHandler,
            TokenUpdateNftsHandler tokenUpdateNftsHandler) {
        this.cryptoCreateHandler = Objects.requireNonNull(cryptoCreateHandler, "cryptoCreateHandler must not be null");
        this.cryptoUpdateHandler = Objects.requireNonNull(cryptoUpdateHandler, "cryptoUpdateHandler must not be null");
        this.cryptoTransferHandler =
                Objects.requireNonNull(cryptoTransferHandler, "cryptoTransferHandler must not be null");
        this.cryptoDeleteHandler = Objects.requireNonNull(cryptoDeleteHandler, "cryptoDeleteHandler must not be null");
        this.cryptoApproveAllowanceHandler =
                Objects.requireNonNull(cryptoApproveAllowanceHandler, "cryptoApproveAllowanceHandler must not be null");
        this.cryptoDeleteAllowanceHandler =
                Objects.requireNonNull(cryptoDeleteAllowanceHandler, "cryptoDeleteAllowanceHandler must not be null");
        this.cryptoAddLiveHashHandler =
                Objects.requireNonNull(cryptoAddLiveHashHandler, "cryptoAddLiveHashHandler must not be null");
        this.cryptoDeleteLiveHashHandler =
                Objects.requireNonNull(cryptoDeleteLiveHashHandler, "cryptoDeleteLiveHashHandler must not be null");
        this.tokenCreateHandler = Objects.requireNonNull(tokenCreateHandler, "tokenCreateHandler must not be null");
        this.tokenUpdateHandler = Objects.requireNonNull(tokenUpdateHandler, "tokenUpdateHandler must not be null");
        this.tokenMintHandler = Objects.requireNonNull(tokenMintHandler, "tokenMintHandler must not be null");
        this.tokenBurnHandler = Objects.requireNonNull(tokenBurnHandler, "tokenBurnHandler must not be null");
        this.tokenDeleteHandler = Objects.requireNonNull(tokenDeleteHandler, "tokenDeleteHandler must not be null");
        this.tokenAccountWipeHandler =
                Objects.requireNonNull(tokenAccountWipeHandler, "tokenAccountWipeHandler must not be null");
        this.tokenFreezeAccountHandler =
                Objects.requireNonNull(tokenFreezeAccountHandler, "tokenFreezeAccountHandler must not be null");
        this.tokenUnfreezeAccountHandler =
                Objects.requireNonNull(tokenUnfreezeAccountHandler, "tokenUnfreezeAccountHandler must not be null");
        this.tokenGrantKycToAccountHandler =
                Objects.requireNonNull(tokenGrantKycToAccountHandler, "tokenGrantKycToAccountHandler must not be null");
        this.tokenRevokeKycFromAccountHandler = Objects.requireNonNull(
                tokenRevokeKycFromAccountHandler, "tokenRevokeKycFromAccountHandler must not be null");
        this.tokenAssociateToAccountHandler = Objects.requireNonNull(
                tokenAssociateToAccountHandler, "tokenAssociateToAccountHandler must not be null");
        this.tokenDissociateFromAccountHandler = Objects.requireNonNull(
                tokenDissociateFromAccountHandler, "tokenDissociateFromAccountHandler must not be null");
        this.tokenFeeScheduleUpdateHandler =
                Objects.requireNonNull(tokenFeeScheduleUpdateHandler, "tokenFeeScheduleUpdateHandler must not be null");
        this.tokenPauseHandler = Objects.requireNonNull(tokenPauseHandler, "tokenPauseHandler must not be null");
        this.tokenUnpauseHandler = Objects.requireNonNull(tokenUnpauseHandler, "tokenUnpauseHandler must not be null");
        this.cryptoGetAccountBalanceHandler = Objects.requireNonNull(
                cryptoGetAccountBalanceHandler, "cryptoGetAccountBalanceHandler must not be null");
        this.cryptoGetAccountInfoHandler =
                Objects.requireNonNull(cryptoGetAccountInfoHandler, "cryptoGetAccountInfoHandler must not be null");
        this.cryptoGetAccountRecordsHandler = Objects.requireNonNull(
                cryptoGetAccountRecordsHandler, "cryptoGetAccountRecordsHandler must not be null");
        this.cryptoGetLiveHashHandler =
                Objects.requireNonNull(cryptoGetLiveHashHandler, "cryptoGetLiveHashHandler must not be null");
        this.cryptoGetStakersHandler =
                Objects.requireNonNull(cryptoGetStakersHandler, "cryptoGetStakersHandler must not be null");
        this.tokenGetInfoHandler = Objects.requireNonNull(tokenGetInfoHandler, "tokenGetInfoHandler must not be null");
        this.tokenGetAccountNftInfosHandler = Objects.requireNonNull(
                tokenGetAccountNftInfosHandler, "tokenGetAccountNftInfosHandler must not be null");
        this.tokenGetNftInfoHandler =
                Objects.requireNonNull(tokenGetNftInfoHandler, "tokenGetNftInfoHandler must not be null");
        this.tokenGetNftInfosHandler =
                Objects.requireNonNull(tokenGetNftInfosHandler, "tokenGetNftInfosHandler must not be null");
        this.tokenUpdateNftsHandler =
                Objects.requireNonNull(tokenUpdateNftsHandler, "tokenUpdateNftsHandler must not be null");
    }

    /**
     * Gets the cryptoCreateHandler.
     *
     * @return the cryptoCreateHandler
     */
    public CryptoCreateHandler cryptoCreateHandler() {
        return cryptoCreateHandler;
    }

    /**
     * Gets the cryptoUpdateHandler.
     *
     * @return the cryptoUpdateHandler
     */
    public CryptoUpdateHandler cryptoUpdateHandler() {
        return cryptoUpdateHandler;
    }

    /**
     * Gets the cryptoTransferHandler.
     *
     * @return the cryptoTransferHandler
     */
    public CryptoTransferHandler cryptoTransferHandler() {
        return cryptoTransferHandler;
    }

    /**
     * Gets the cryptoDeleteHandler.
     *
     * @return the cryptoDeleteHandler
     */
    public CryptoDeleteHandler cryptoDeleteHandler() {
        return cryptoDeleteHandler;
    }

    /**
     * Gets the cryptoApproveAllowanceHandler.
     *
     * @return the cryptoApproveAllowanceHandler
     */
    public CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler() {
        return cryptoApproveAllowanceHandler;
    }

    /**
     * Gets the cryptoDeleteAllowanceHandler.
     *
     * @return the cryptoDeleteAllowanceHandler
     */
    public CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler() {
        return cryptoDeleteAllowanceHandler;
    }

    /**
     * Gets the cryptoAddLiveHashHandler.
     *
     * @return the cryptoAddLiveHashHandler
     */
    public CryptoAddLiveHashHandler cryptoAddLiveHashHandler() {
        return cryptoAddLiveHashHandler;
    }

    /**
     * Gets the cryptoDeleteLiveHashHandler.
     *
     * @return the cryptoDeleteLiveHashHandler
     */
    public CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler() {
        return cryptoDeleteLiveHashHandler;
    }

    /**
     * Gets the tokenCreateHandler.
     *
     * @return the tokenCreateHandler
     */
    public TokenCreateHandler tokenCreateHandler() {
        return tokenCreateHandler;
    }

    /**
     * Gets the tokenUpdateHandler.
     *
     * @return the tokenUpdateHandler
     */
    public TokenUpdateHandler tokenUpdateHandler() {
        return tokenUpdateHandler;
    }

    /**
     * Gets the tokenMintHandler.
     *
     * @return the tokenMintHandler
     */
    public TokenMintHandler tokenMintHandler() {
        return tokenMintHandler;
    }

    /**
     * Gets the tokenBurnHandler.
     *
     * @return the tokenBurnHandler
     */
    public TokenBurnHandler tokenBurnHandler() {
        return tokenBurnHandler;
    }

    /**
     * Gets the tokenDeleteHandler.
     *
     * @return the tokenDeleteHandler
     */
    public TokenDeleteHandler tokenDeleteHandler() {
        return tokenDeleteHandler;
    }

    /**
     * Gets the tokenAccountWipeHandler.
     *
     * @return the tokenAccountWipeHandler
     */
    public TokenAccountWipeHandler tokenAccountWipeHandler() {
        return tokenAccountWipeHandler;
    }

    /**
     * Gets the tokenFreezeAccountHandler.
     *
     * @return the tokenFreezeAccountHandler
     */
    public TokenFreezeAccountHandler tokenFreezeAccountHandler() {
        return tokenFreezeAccountHandler;
    }

    /**
     * Gets the tokenUnfreezeAccountHandler.
     *
     * @return the tokenUnfreezeAccountHandler
     */
    public TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler() {
        return tokenUnfreezeAccountHandler;
    }

    /**
     * Gets the tokenGrantKycToAccountHandler.
     *
     * @return the tokenGrantKycToAccountHandler
     */
    public TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler() {
        return tokenGrantKycToAccountHandler;
    }

    /**
     * Gets the tokenRevokeKycFromAccountHandler.
     *
     * @return the tokenRevokeKycFromAccountHandler
     */
    public TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler() {
        return tokenRevokeKycFromAccountHandler;
    }

    /**
     * Gets the tokenAssociateToAccountHandler.
     *
     * @return the tokenAssociateToAccountHandler
     */
    public TokenAssociateToAccountHandler tokenAssociateToAccountHandler() {
        return tokenAssociateToAccountHandler;
    }

    /**
     * Gets the tokenDissociateFromAccountHandler.
     *
     * @return the tokenDissociateFromAccountHandler
     */
    public TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler() {
        return tokenDissociateFromAccountHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler() {
        return tokenFeeScheduleUpdateHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public TokenPauseHandler tokenPauseHandler() {
        return tokenPauseHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public TokenUnpauseHandler tokenUnpauseHandler() {
        return tokenUnpauseHandler;
    }

    /**
     * Gets the tokenFeeScheduleUpdateHandler.
     *
     * @return the tokenFeeScheduleUpdateHandler
     */
    public CryptoGetAccountBalanceHandler cryptoGetAccountBalanceHandler() {
        return cryptoGetAccountBalanceHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetAccountInfoHandler cryptoGetAccountInfoHandler() {
        return cryptoGetAccountInfoHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetAccountRecordsHandler cryptoGetAccountRecordsHandler() {
        return cryptoGetAccountRecordsHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetLiveHashHandler cryptoGetLiveHashHandler() {
        return cryptoGetLiveHashHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public CryptoGetStakersHandler cryptoGetStakersHandler() {
        return cryptoGetStakersHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetInfoHandler tokenGetInfoHandler() {
        return tokenGetInfoHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetAccountNftInfosHandler tokenGetAccountNftInfosHandler() {
        return tokenGetAccountNftInfosHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetNftInfoHandler tokenGetNftInfoHandler() {
        return tokenGetNftInfoHandler;
    }

    /**
     * Gets the cryptoGetAccountRecordsHandler.
     *
     * @return the cryptoGetAccountRecordsHandler
     */
    public TokenGetNftInfosHandler tokenGetNftInfosHandler() {
        return tokenGetNftInfosHandler;
    }

    /**
     * Gets the tokenUpdateNftsHandler.
     *
     * @return the tokenUpdateNftsHandler
     */
    public TokenUpdateNftsHandler tokenUpdateNftsHandler() {
        return tokenUpdateNftsHandler;
    }
}
