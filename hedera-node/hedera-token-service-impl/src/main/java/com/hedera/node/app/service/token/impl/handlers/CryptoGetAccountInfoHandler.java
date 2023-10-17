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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.tokenRelationshipsOf;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.AccountInfo;
import com.hedera.hapi.node.token.CryptoGetInfoQuery;
import com.hedera.hapi.node.token.CryptoGetInfoResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage;
import com.hedera.node.app.service.mono.fees.calculation.crypto.queries.GetAccountInfoResourceUsage;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.api.AccountSummariesApi;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_GET_INFO}.
 */
@Singleton
public class CryptoGetAccountInfoHandler extends PaidQueryHandler {
    private final CryptoOpsUsage cryptoOpsUsage;

    @Inject
    public CryptoGetAccountInfoHandler(final CryptoOpsUsage cryptoOpsUsage) {
        this.cryptoOpsUsage = cryptoOpsUsage;
        // Dagger2
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.cryptoGetInfoOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = CryptoGetInfoResponse.newBuilder().header(requireNonNull(header));
        return Response.newBuilder().cryptoGetInfo(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final CryptoGetInfoQuery op = query.cryptoGetInfoOrThrow();
        if (op.hasAccountID()) {
            final var account = accountStore.getAccountById(requireNonNull(op.accountID()));
            validateFalsePreCheck(account == null, INVALID_ACCOUNT_ID);
            validateFalsePreCheck(account.deleted(), ACCOUNT_DELETED);
        } else {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var op = query.cryptoGetInfoOrThrow();
        final var response = CryptoGetInfoResponse.newBuilder();
        final var accountId = op.accountIDOrElse(AccountID.DEFAULT);

        response.header(header);
        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = infoForAccount(accountId, context);
            if (optionalInfo.isPresent()) {
                response.accountInfo(optionalInfo.get());
            } else {
                response.header(ResponseHeader.newBuilder()
                        .nodeTransactionPrecheckCode(FAIL_INVALID)
                        .cost(0)); // FUTURE: from mono service, check in EET
            }
        }

        return Response.newBuilder().cryptoGetInfo(response).build();
    }

    /**
     * Provides information about an account.
     *
     * @param accountID account id
     * @param context the query context
     * @return the information about the account
     */
    private Optional<AccountInfo> infoForAccount(
            @NonNull final AccountID accountID, @NonNull final QueryContext context) {
        requireNonNull(accountID);
        requireNonNull(context);

        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenRelationStore = context.createStore(ReadableTokenRelationStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var stakingInfoStore = context.createStore(ReadableStakingInfoStore.class);
        final var stakingRewardsStore = context.createStore(ReadableNetworkStakingRewardsStore.class);

        final var account = accountStore.getAccountById(accountID);
        if (account == null) {
            return Optional.empty();
        } else {
            final var info = AccountInfo.newBuilder();
            info.ledgerId(ledgerConfig.id());
            if (!isEmpty(account.key())) info.key(account.key());
            // Set this field with the account's id since that's guaranteed to be a numeric 0.0.X id;
            // the request might have been made using a 0.0.<alias> id
            info.accountID(account.accountIdOrThrow());
            info.receiverSigRequired(account.receiverSigRequired());
            info.deleted(account.deleted());
            info.memo(account.memo());
            info.autoRenewPeriod(Duration.newBuilder().seconds(account.autoRenewSeconds()));
            info.balance(account.tinybarBalance());
            info.expirationTime(Timestamp.newBuilder().seconds(account.expirationSecond()));
            info.contractAccountID(AccountSummariesApi.hexedEvmAddressOf(account));
            info.ownedNfts(account.numberOwnedNfts());
            info.maxAutomaticTokenAssociations(account.maxAutoAssociations());
            info.ethereumNonce(account.ethereumNonce());
            //  info.proxyAccountID(); Deprecated
            info.alias(account.alias());
            info.tokenRelationships(
                    tokenRelationshipsOf(account, tokenStore, tokenRelationStore, tokensConfig.maxRelsPerInfoQuery()));
            info.stakingInfo(AccountSummariesApi.summarizeStakingInfo(
                    stakingConfig.rewardHistoryNumStoredPeriods(),
                    stakingConfig.periodMins(),
                    stakingRewardsStore.isStakingRewardsActivated(),
                    account,
                    stakingInfoStore));
            return Optional.of(info.build());
        }
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull final QueryContext queryContext) {
        final var query = queryContext.query();
        final var accountStore = queryContext.createStore(ReadableAccountStore.class);
        final var op = query.cryptoGetInfoOrThrow();
        final var accountId = op.accountIDOrThrow();
        final var account = accountStore.getAccountById(accountId);

        return queryContext.feeCalculator().legacyCalculate(sigValueObj -> new GetAccountInfoResourceUsage(
                        cryptoOpsUsage, null, null, null)
                .usageGiven(query, account));
    }
}
