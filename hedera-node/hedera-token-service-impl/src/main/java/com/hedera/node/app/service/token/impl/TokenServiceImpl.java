/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.token.TokenService;
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
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** An implementation of the {@link TokenService} interface. */
public class TokenServiceImpl implements TokenService {

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
    }

    @NonNull
    public TokenAccountWipeHandler getTokenAccountWipeHandler() {
        return tokenAccountWipeHandler;
    }

    @NonNull
    public TokenAssociateToAccountHandler getTokenAssociateToAccountHandler() {
        return tokenAssociateToAccountHandler;
    }

    @NonNull
    public TokenBurnHandler getTokenBurnHandler() {
        return tokenBurnHandler;
    }

    @NonNull
    public TokenCreateHandler getTokenCreateHandler() {
        return tokenCreateHandler;
    }

    @NonNull
    public TokenDeleteHandler getTokenDeleteHandler() {
        return tokenDeleteHandler;
    }

    @NonNull
    public TokenDissociateFromAccountHandler getTokenDissociateFromAccountHandler() {
        return tokenDissociateFromAccountHandler;
    }

    @NonNull
    public TokenFeeScheduleUpdateHandler getTokenFeeScheduleUpdateHandler() {
        return tokenFeeScheduleUpdateHandler;
    }

    @NonNull
    public TokenFreezeAccountHandler getTokenFreezeAccountHandler() {
        return tokenFreezeAccountHandler;
    }

    @NonNull
    public TokenGetAccountNftInfosHandler getTokenGetAccountNftInfosHandler() {
        return tokenGetAccountNftInfosHandler;
    }

    @NonNull
    public TokenGetInfoHandler getTokenGetInfoHandler() {
        return tokenGetInfoHandler;
    }

    @NonNull
    public TokenGetNftInfoHandler getTokenGetNftInfoHandler() {
        return tokenGetNftInfoHandler;
    }

    @NonNull
    public TokenGetNftInfosHandler getTokenGetNftInfosHandler() {
        return tokenGetNftInfosHandler;
    }

    @NonNull
    public TokenGrantKycToAccountHandler getTokenGrantKycToAccountHandler() {
        return tokenGrantKycToAccountHandler;
    }

    @NonNull
    public TokenMintHandler getTokenMintHandler() {
        return tokenMintHandler;
    }

    @NonNull
    public TokenPauseHandler getTokenPauseHandler() {
        return tokenPauseHandler;
    }

    @NonNull
    public TokenRevokeKycFromAccountHandler getTokenRevokeKycFromAccountHandler() {
        return tokenRevokeKycFromAccountHandler;
    }

    @NonNull
    public TokenUnfreezeAccountHandler getTokenUnfreezeAccountHandler() {
        return tokenUnfreezeAccountHandler;
    }

    @NonNull
    public TokenUnpauseHandler getTokenUnpauseHandler() {
        return tokenUnpauseHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
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
                tokenUnpauseHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(
                tokenGetAccountNftInfosHandler,
                tokenGetInfoHandler,
                tokenGetNftInfoHandler,
                tokenGetNftInfosHandler);
    }
}
