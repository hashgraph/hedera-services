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
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TokenBalance;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoGetAccountBalanceQuery;
import com.hedera.hapi.node.token.CryptoGetAccountBalanceResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_GET_ACCOUNT_BALANCE}.
 */
@Singleton
public class CryptoGetAccountBalanceHandler extends FreeQueryHandler {
    @Inject
    public CryptoGetAccountBalanceHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.cryptogetAccountBalanceOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = CryptoGetAccountBalanceResponse.newBuilder().header(requireNonNull(header));
        return Response.newBuilder().cryptogetAccountBalance(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final CryptoGetAccountBalanceQuery op = query.cryptogetAccountBalanceOrThrow();
        if (op.hasAccountID()) {
            final var account = accountStore.getAccountById(requireNonNull(op.accountID()));
            validateFalsePreCheck(account == null, INVALID_ACCOUNT_ID);
            validateFalsePreCheck(account.deleted(), ACCOUNT_DELETED);
        } else if (op.hasContractID()) {
            final var contract = accountStore.getContractById(requireNonNull(op.contractID()));
            validateFalsePreCheck(contract == null || !contract.smartContract(), INVALID_CONTRACT_ID);
            validateFalsePreCheck(contract.deleted(), CONTRACT_DELETED);
        } else {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var config = context.configuration().getConfigData(TokensConfig.class);
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenRelationStore = context.createStore(ReadableTokenRelationStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var op = query.cryptogetAccountBalanceOrThrow();
        final var response = CryptoGetAccountBalanceResponse.newBuilder();
        final var accountId = op.accountIDOrElse(AccountID.DEFAULT);

        response.header(header);
        if (header.nodeTransactionPrecheckCode() == OK) {
            response.accountID(accountId);
            final var account = accountStore.getAccountById(accountId);
            response.balance(requireNonNull(account).tinybarBalance());
            response.tokenBalances(getTokenBalances(config, account, tokenStore, tokenRelationStore));
        }

        return Response.newBuilder().cryptogetAccountBalance(response).build();
    }

    /**
     * Calculate TokenBalance of an Account
     * @param tokenConfig use TokenConfig to get maxRelsPerInfoQuery value
     * @param account the account to be calculated from
     * @param readableTokenStore readable token store
     * @param tokenRelationStore token relation store
     * @return ArrayList of TokenBalance object
     */
    private List<TokenBalance> getTokenBalances(
            @NonNull final TokensConfig tokenConfig,
            @NonNull final Account account,
            @NonNull final ReadableTokenStore readableTokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore) {
        final var ret = new ArrayList<TokenBalance>();
        var tokenNum = account.headTokenNumber();
        int count = 0;
        TokenRelation tokenRelation;
        Token token; // token from readableToken store by tokenID
        TokenID tokenID; // build from tokenNum
        AccountID accountID; // build from accountNumber
        TokenBalance tokenBalance; // created TokenBalance object
        while (tokenNum != 0 && count < tokenConfig.maxRelsPerInfoQuery()) {
            accountID =
                    AccountID.newBuilder().accountNum(account.accountNumber()).build();
            tokenID = TokenID.newBuilder().tokenNum(tokenNum).build();
            tokenRelation = tokenRelationStore.get(accountID, tokenID);
            if (tokenRelation != null) {
                token = readableTokenStore.get(tokenID);
                if (token != null) {
                    tokenBalance = TokenBalance.newBuilder()
                            .tokenId(TokenID.newBuilder().tokenNum(tokenNum).build())
                            .balance(tokenRelation.balance())
                            .decimals(token.decimals())
                            .build();
                    ret.add(tokenBalance);
                }
                tokenNum = tokenRelation.nextToken();
            } else {
                break;
            }
            count++;
        }
        return ret;
    }
}
