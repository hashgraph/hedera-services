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
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
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
        requireNonNull(customFeesValidator);
        this.customFeesValidator = customFeesValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());

        final var op = context.body().tokenFeeScheduleUpdateOrThrow();
        final var tokenId = op.tokenIdOrElse(TokenID.DEFAULT);

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
     * Handles a transaction with {@link HederaFunctionality#TOKEN_FEE_SCHEDULE_UPDATE}
     * @param context the context of the transaction
     */
    @Override
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);

        final var txn = context.body();

        // get the latest configuration
        final var config = context.configuration().getConfigData(TokensConfig.class);
        final var op = txn.tokenFeeScheduleUpdateOrThrow();

        // validate checks in handle
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var token = validateSemantics(op, tokenStore, config);

        // create readable stores from the context
        final var readableAccountStore = context.readableStore(ReadableAccountStore.class);
        final var readableTokenRelsStore = context.readableStore(ReadableTokenRelationStore.class);

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
            @NonNull final TokenFeeScheduleUpdateTransactionBody op,
            @NonNull final WritableTokenStore tokenStore,
            @NonNull final TokensConfig config) {
        var token = tokenStore.get(op.tokenIdOrElse(TokenID.DEFAULT));
        validateTrue(token != null, INVALID_TOKEN_ID);
        validateTrue(token.hasFeeScheduleKey(), TOKEN_HAS_NO_FEE_SCHEDULE_KEY);
        validateTrue(op.customFees().size() <= config.maxCustomFeesAllowed(), CUSTOM_FEES_LIST_TOO_LONG);
        return token;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenFeeScheduleUpdateOrThrow();
        if (!op.hasTokenId()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }
    }
}
