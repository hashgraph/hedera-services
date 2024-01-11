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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_PAUSE}.
 */
@Singleton
public class TokenPauseHandler implements TransactionHandler {
    @Inject
    public TokenPauseHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        preCheck(context);
        final var op = context.body().tokenPause();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }
        if (tokenMeta.hasPauseKey()) {
            context.requireKey(tokenMeta.pauseKey());
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);

        final var op = handleContext.body().tokenPause();
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        var token = tokenStore.get(op.tokenOrElse(TokenID.DEFAULT));
        validateTrue(token != null, INVALID_TOKEN_ID);
        validateTrue(token.hasPauseKey(), TOKEN_HAS_NO_PAUSE_KEY);

        final var copyBuilder = token.copyBuilder();
        copyBuilder.paused(true);
        tokenStore.put(copyBuilder.build());
    }

    /**
     * Validate semantics for the given transaction body.
     * @param context the {@link PreHandleContext} which collects all information that will be
     *                passed to {@link #handle}
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if the transaction body is invalid
     */
    private void preCheck(@NonNull final PreHandleContext context) throws PreCheckException {
        final var op = context.body().tokenPause();
        if (!op.hasToken()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var meta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();
        return feeContext
                .feeCalculator(SubType.DEFAULT)
                .addBytesPerTransaction(meta.getBpt())
                .calculate();
    }
}
