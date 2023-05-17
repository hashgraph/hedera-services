/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.hapi.node.token.GetAccountDetailsQuery;
import com.hedera.hapi.node.token.GetAccountDetailsResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#GET_ACCOUNT_DETAILS}.
 */
@Singleton
public class NetworkGetAccountDetailsHandler extends PaidQueryHandler {

    private final NetworkInfo networkInfo;

    @Inject
    public NetworkGetAccountDetailsHandler() {
        // Exists for injection
        this.networkInfo = null;

    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.accountDetailsOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = GetAccountDetailsResponse.newBuilder().header(header);
        return Response.newBuilder().accountDetails(response).build();
    }


    @Override
    public boolean requiresNodePayment(@NonNull ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull ResponseType responseType) {
        return COST_ANSWER == responseType;
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final GetAccountDetailsQuery op = query.accountDetailsOrThrow();
        if (op.hasAccountId()) {
            final var accountMetadata = accountStore.getAccountById(op.accountIdOrElse(AccountID.DEFAULT));
            mustExist(accountMetadata, INVALID_ACCOUNT_ID);
            if (accountMetadata.deleted()) {
                throw new PreCheckException(INVALID_ACCOUNT_ID);
            }
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var op = query.accountDetailsOrThrow();
        final var responseBuilder = GetAccountDetailsResponse.newBuilder();
        final var account = op.accountIdOrElse(AccountID.DEFAULT);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        responseBuilder.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = infoForAccount(account, accountStore);
            optionalInfo.ifPresent(responseBuilder::accountDetails);
        }

        return Response.newBuilder().accountDetails(responseBuilder).build();
    }

    /**
     * Provides information about an account.
     * @param accountID the account to get information about
     * @param accountStore the account store
     * @return the information about the account
     */
    private Optional<AccountDetails> infoForAccount(@NonNull final AccountID accountID, @NonNull final ReadableAccountStore accountStore) {
        final var account = accountStore.getAccountById(accountID);
        if (account == null) {
            return Optional.empty();
        } else {
               /*       @Nullable AccountID accountId,
        String contractAccountId,
        boolean deleted,
        @Nullable AccountID proxyAccountId,
        long proxyReceived,
        @Nullable Key key,
        long balance,
        boolean receiverSigRequired,
        @Nullable Timestamp expirationTime,
        @Nullable Duration autoRenewPeriod,
        @Nullable List<TokenRelationship> tokenRelationships,
        String memo,
        long ownedNfts,
        int maxAutomaticTokenAssociations,
        Bytes alias,
        Bytes ledgerId,
        @Nullable List<GrantedCryptoAllowance> grantedCryptoAllowances,
        @Nullable List<GrantedNftAllowance> grantedNftAllowances,
        @Nullable List<GrantedTokenAllowance> grantedTokenAllowances*/
            final var info = AccountDetails.newBuilder();
            final AccountID accountID = account.alias().length() <= 0 ? id : accountNum.toGrpcAccountId();
            info.accountId(account.accountNumber()); // how to convert it
            info.contractAccountId(account.contractAccountId()); //not exist in account
            info.deleted(account.deleted());
            if (!isEmpty(account.key())) info.key(account.key());
            info.balance(account.tinybarBalance());
            info.receiverSigRequired(account.receiverSigRequired());
            if (!isEmpty(account.expirationTime())) info.expirationTime(account.expirationTime());//not exist in account
            if (account.autoRenewSecs()) info.autoRenewPeriod(Duration.newBuilder().seconds(account.autoRenewSecs()));//not exist in account
            info.tokenRelationships(account.tokenRelationships); //not exist in account
            info.memo(account.memo());
            info.ownedNfts(account.numberOwnedNfts());
            info.maxAutomaticTokenAssociations(account.maxAutoAssociations());
            info.alias(account.alias());
            info.ledgerId(account.ledgerId());//not exist in account
            info.grantedCryptoAllowances(account.cryptoAllowances());//is it right mapping
            info.grantedNftAllowances(account.approveForAllNftAllowances());//is it right mapping
            info.grantedTokenAllowances(account.tokenAllowances());//is it right mapping


            info.ledgerId(networkInfo.ledgerId());
            return Optional.of(info.build());
        }
    }
}
