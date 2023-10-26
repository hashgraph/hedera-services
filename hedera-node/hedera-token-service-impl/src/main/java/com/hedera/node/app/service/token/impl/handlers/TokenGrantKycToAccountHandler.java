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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.txnEstimateFactory;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.fees.calculation.token.txns.TokenGrantKycResourceUsage;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_GRANT_KYC_TO_ACCOUNT}.
 */
@Singleton
public class TokenGrantKycToAccountHandler implements TransactionHandler {

    @Inject
    public TokenGrantKycToAccountHandler() {}

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenGrantKycOrThrow();
        pureChecks(context.body());

        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
        validateTruePreCheck(tokenMeta.hasKycKey(), TOKEN_HAS_NO_KYC_KEY);
        context.requireKey(tokenMeta.kycKey());
    }

    /**
     * Performs checks independent of state or context
     */
    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenGrantKycOrThrow();
        if (!op.hasToken()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }

        if (!op.hasAccount()) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param handleContext the {@link HandleContext} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);

        final var txnBody = handleContext.body();
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);

        final var op = txnBody.tokenGrantKycOrThrow();

        final var targetTokenId = op.tokenOrThrow();
        final var targetAccountId = op.accountOrThrow();
        final var accountStore = handleContext.readableStore(ReadableAccountStore.class);
        final var expiryValidator = handleContext.expiryValidator();
        final var tokenRelation = validateSemantics(
                targetAccountId, targetTokenId, tokenRelStore, accountStore, expiryValidator, tokenStore);

        final var tokenRelBuilder = tokenRelation.copyBuilder();
        tokenRelBuilder.kycGranted(true);
        tokenRelStore.put(tokenRelBuilder.build());
    }

    /**
     * Performs checks that the entities related to this transaction exist and are valid
     *
     * @return the token relation for the given token and account
     */
    @NonNull
    private TokenRelation validateSemantics(
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
            @NonNull final WritableTokenRelationStore tokenRelStore,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final ReadableTokenStore tokenStore)
            throws HandleException {
        final var account =
                TokenHandlerHelper.getIfUsable(accountId, accountStore, expiryValidator, INVALID_ACCOUNT_ID);
        final var token = TokenHandlerHelper.getIfUsable(tokenId, tokenStore);
        final var tokenRel = TokenHandlerHelper.getIfUsable(accountId, tokenId, tokenRelStore);

        return tokenRel;
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body();

        return feeContext.feeCalculator(SubType.DEFAULT).legacyCalculate(sigValueObj -> new TokenGrantKycResourceUsage(
                        txnEstimateFactory)
                .usageGiven(fromPbj(op), sigValueObj, null));
    }
}
