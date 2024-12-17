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
import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.txnEstimateFactory;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.token.TokenDeleteUsage;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.service.token.records.TokenBaseStreamBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.FeeData;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_DELETE}.
 */
@Singleton
public class TokenDeleteHandler implements TransactionHandler {
    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenDeleteHandler() {}

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenDeletionOrThrow();
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMetadata = tokenStore.getTokenMeta(tokenId);
        if (tokenMetadata == null) throw new PreCheckException(INVALID_TOKEN_ID);
        // we will fail in handle() if token has no admin key (no need to fail here)
        if (tokenMetadata.hasAdminKey()) {
            context.requireKey(tokenMetadata.adminKey());
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenDeletionOrThrow();

        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var storeFactory = context.storeFactory();
        final var tokenStore = storeFactory.writableStore(WritableTokenStore.class);
        final var accountStore = storeFactory.writableStore(WritableAccountStore.class);
        final var txn = context.body();
        final var op = txn.tokenDeletionOrThrow();
        final var tokenId = op.tokenOrThrow();
        final var token = validateSemantics(tokenId, tokenStore);

        // Update the token to be deleted
        final var updatedToken = token.copyBuilder().deleted(true).build();
        tokenStore.put(updatedToken);

        // Update the token treasury account's treasury titles count
        final var account = accountStore.get(token.treasuryAccountId());
        final var updatedAccount = account.copyBuilder()
                .numberTreasuryTitles(account.numberTreasuryTitles() - 1)
                .build();
        accountStore.put(updatedAccount);

        final var record = context.savepointStack().getBaseBuilder(TokenBaseStreamBuilder.class);
        record.tokenType(updatedToken.tokenType());
    }

    /**
     * Validates the semantics of the token to be deleted.
     * @param tokenId the token to be deleted
     * @param tokenStore the token store
     * @return the token to be deleted
     */
    @NonNull
    public Token validateSemantics(@NonNull final TokenID tokenId, @NonNull final ReadableTokenStore tokenStore) {
        final var token = TokenHandlerHelper.getIfUsable(tokenId, tokenStore);

        validateTrue(token.adminKey() != null, ResponseCodeEnum.TOKEN_IS_IMMUTABLE);

        return token;
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body();
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(sigValueObj -> usageGiven(CommonPbjConverters.fromPbj(op), sigValueObj));
    }

    private FeeData usageGiven(final com.hederahashgraph.api.proto.java.TransactionBody txn, final SigValueObj svo) {
        final var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        final var estimate = TokenDeleteUsage.newEstimate(txn, txnEstimateFactory.get(sigUsage, txn, ESTIMATOR_UTILS));
        return estimate.get();
    }
}
