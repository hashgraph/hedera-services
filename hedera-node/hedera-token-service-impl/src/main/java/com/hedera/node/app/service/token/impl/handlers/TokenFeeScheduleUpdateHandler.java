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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.config.TokenServiceConfig;
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_FEE_SCHEDULE_UPDATE}.
 */
@Singleton
public class TokenFeeScheduleUpdateHandler implements TransactionHandler {
    private final CustomFeesValidator customFeesValidator;

    @Inject
    public TokenFeeScheduleUpdateHandler(@NonNull final CustomFeesValidator customFeesValidator) {
        this.customFeesValidator = customFeesValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenFeeScheduleUpdateOrThrow();
        final var tokenId = op.tokenIdOrElse(TokenID.DEFAULT);
        pureChecks(op);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMetadata = tokenStore.getTokenMeta(tokenId);
        if (tokenMetadata == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (tokenMetadata.hasFeeScheduleKey()) {
            context.requireKey(tokenMetadata.feeScheduleKey());
            for (final var customFee : op.customFeesOrElse(emptyList())) {
                final var collector = customFee.feeCollectorAccountIdOrElse(AccountID.DEFAULT);
                context.requireKeyIfReceiverSigRequired(collector, INVALID_CUSTOM_FEE_COLLECTOR);
            }
        }
        // we do not set a failure status if a fee schedule key is not present for the token,
        // we choose to fail with TOKEN_HAS_NO_FEE_SCHEDULE_KEY in the handle() method
    }

    /**
     * {@inheritDoc}
     */
    public void handle(
            @NonNull final HandleContext context,
            @NonNull final TransactionBody txn,
            @NonNull final WritableTokenStore tokenStore) {
        requireNonNull(context);
        requireNonNull(txn);
        requireNonNull(tokenStore);

        // get the latest configuration
        final var config = context.getConfiguration().getConfigData(TokenServiceConfig.class);
        var op = txn.tokenFeeScheduleUpdateOrThrow();

        // validate checks in handle
        final var token = validateSemantics(op, tokenStore, config);
        // create readable stores from the context
        final var readableAccountStore = context.createReadableStore(ReadableAccountStore.class);
        final var readableTokenRelsStore = context.createReadableStore(ReadableTokenRelationStore.class);

        // validate custom fees before committing
        customFeesValidator.validateForFeeScheduleUpdate(
                token, readableAccountStore, readableTokenRelsStore, tokenStore, op.customFees());
        // set the custom fees on token
        final var copy = token.copyBuilder().customFees(op.customFees());
        // add token to the modifications map
        tokenStore.put(copy.build());
    }

    /**
     * Validate semantics of the transaction in handle call. This method is called before the
     * transaction is handled.
     * @param op the transaction body
     * @param tokenStore the token store
     * @param config the token service config
     * @return the token
     */
    private Token validateSemantics(
            TokenFeeScheduleUpdateTransactionBody op, WritableTokenStore tokenStore, TokenServiceConfig config) {
        var token = tokenStore.get(op.tokenIdOrElse(TokenID.DEFAULT).tokenNum());
        validateTrue(token.isPresent(), INVALID_TOKEN_ID);
        validateTrue(token.get().hasFeeScheduleKey(), TOKEN_HAS_NO_FEE_SCHEDULE_KEY);
        validateTrue(op.customFees().size() <= config.maxCustomFeesAllowed(), CUSTOM_FEES_LIST_TOO_LONG);
        return token.get();
    }

    private void pureChecks(@NonNull final TokenFeeScheduleUpdateTransactionBody op) throws PreCheckException {
        if (!op.hasTokenId()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }
    }
}
