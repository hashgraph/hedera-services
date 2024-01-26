/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FREEZE_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FROZEN;
import static com.hedera.hapi.node.base.TokenFreezeStatus.UNFROZEN;
import static com.hedera.hapi.node.base.TokenKycStatus.GRANTED;
import static com.hedera.hapi.node.base.TokenKycStatus.KYC_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenKycStatus.REVOKED;
import static com.hedera.hapi.node.base.TokenPauseStatus.PAUSED;
import static com.hedera.hapi.node.base.TokenPauseStatus.PAUSE_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenPauseStatus.UNPAUSED;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenGetInfoResponse;
import com.hedera.hapi.node.token.TokenInfo;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.mono.fees.calculation.token.queries.GetTokenInfoResourceUsage;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_GET_INFO}.
 */
@Singleton
public class TokenGetInfoHandler extends PaidQueryHandler {
    @Inject
    public TokenGetInfoHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.tokenGetInfoOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = TokenGetInfoResponse.newBuilder().header(requireNonNull(header));
        return Response.newBuilder().tokenGetInfo(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var op = query.tokenGetInfoOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);

        final var token = tokenStore.get(requireNonNull(op.token()));
        validateFalsePreCheck(token == null, INVALID_TOKEN_ID);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var config = context.configuration().getConfigData(LedgerConfig.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var op = query.tokenGetInfoOrThrow();
        final var response = TokenGetInfoResponse.newBuilder();
        final var tokenID = op.tokenOrElse(TokenID.DEFAULT);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        response.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = infoForToken(tokenID, tokenStore, config);
            if (optionalInfo.isPresent()) {
                response.tokenInfo(optionalInfo.get());
            } else {
                response.header(ResponseHeader.newBuilder()
                        .nodeTransactionPrecheckCode(INVALID_TOKEN_ID)
                        .cost(0)); // FUTURE: from mono service, check in EET
            }
        }

        return Response.newBuilder().tokenGetInfo(response).build();
    }

    /**
     * Returns the {@link TokenInfo} for the given {@link TokenID}, if it exists.
     *
     * @param tokenID
     * 		the {@link TokenID} for which to return the {@link TokenInfo}
     * @param readableTokenStore
     * 		the {@link ReadableTokenStore} from which to retrieve the {@link TokenInfo}
     * @param config
     * 		the {@link LedgerConfig} containing the ledger ID
     * @return the {@link TokenInfo} for the given {@link TokenID}, if it exists
     */
    private Optional<TokenInfo> infoForToken(
            @NonNull final TokenID tokenID,
            @NonNull final ReadableTokenStore readableTokenStore,
            @NonNull final LedgerConfig config) {
        requireNonNull(tokenID);
        requireNonNull(readableTokenStore);
        requireNonNull(config);

        final var token = readableTokenStore.get(tokenID);
        if (token == null) {
            return Optional.empty();
        } else {
            final var info = TokenInfo.newBuilder();
            info.ledgerId(config.id());
            info.tokenType(token.tokenType());
            info.supplyType(token.supplyType());
            info.tokenId(tokenID);
            info.deleted(token.deleted());
            info.symbol(token.symbol());
            info.name(token.name());
            info.memo(token.memo());
            info.treasury(token.treasuryAccountId());
            info.totalSupply(token.totalSupply());
            info.maxSupply(token.maxSupply());
            info.decimals(token.decimals());
            info.expiry(Timestamp.newBuilder().seconds(token.expirationSecond()));
            if (!isEmpty(token.adminKey())) info.adminKey(token.adminKey());
            if (!isEmpty(token.supplyKey())) {
                info.supplyKey(token.supplyKey());
            }
            if (!isEmpty(token.wipeKey())) {
                info.wipeKey(token.wipeKey());
            }
            if (!isEmpty(token.feeScheduleKey())) {
                info.feeScheduleKey(token.feeScheduleKey());
            }

            if (token.autoRenewAccountId() != null) {
                info.autoRenewAccount(token.autoRenewAccountId());
                info.autoRenewPeriod(Duration.newBuilder().seconds(token.autoRenewSeconds()));
            }

            if (!isEmpty(token.freezeKey())) {
                info.freezeKey(token.freezeKey());
                info.defaultFreezeStatus(token.accountsFrozenByDefault() ? FROZEN : UNFROZEN);
            } else {
                info.defaultFreezeStatus(FREEZE_NOT_APPLICABLE);
            }
            if (!isEmpty(token.kycKey())) {
                info.kycKey(token.kycKey());
                info.defaultKycStatus(token.accountsKycGrantedByDefault() ? GRANTED : REVOKED);
            } else {
                info.defaultKycStatus(KYC_NOT_APPLICABLE);
            }

            if (!isEmpty(token.pauseKey())) {
                info.pauseKey(token.pauseKey());
                info.pauseStatus(token.paused() ? PAUSED : UNPAUSED);
            } else {
                info.pauseStatus(PAUSE_NOT_APPLICABLE);
            }
            if (!isEmpty(token.metadataKey())) {
                info.metadataKey(token.metadataKey());
            }
            info.metadata(token.metadata());
            info.customFees(token.customFees());

            return Optional.of(info.build());
        }
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull final QueryContext queryContext) {
        final var query = queryContext.query();
        final var tokenStore = queryContext.createStore(ReadableTokenStore.class);
        final var op = query.tokenGetInfoOrThrow();
        final var tokenId = op.tokenOrThrow();
        final var token = tokenStore.get(tokenId);

        return queryContext.feeCalculator().legacyCalculate(sigValueObj -> new GetTokenInfoResourceUsage()
                .usageGiven(fromPbj(query), token));
    }
}
