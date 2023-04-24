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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.records.BaseRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_UNPAUSE}.
 */
@Singleton
public class TokenUnpauseHandler implements TransactionHandler {
    @Inject
    public TokenUnpauseHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, and warms the cache.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull final ReadableTokenStore tokenStore)
            throws PreCheckException {
        requireNonNull(context);
        requireNonNull(tokenStore);

        preCheck(context);

        final var op = context.body().tokenUnpause();
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
     * @param txn the {@link TokenPauseTransactionBody} of the active transaction
     * @param tokenStore the {@link WritableTokenStore} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionBody txn, @NonNull final WritableTokenStore tokenStore) {
        requireNonNull(txn);
        requireNonNull(tokenStore);

        var op = txn.tokenUnpause();
        var token = tokenStore.get(op.token().tokenNum());
        if (token.isEmpty()) {
            throw new HandleException(INVALID_TOKEN_ID);
        }

        final var copyBuilder = token.get().copyBuilder();
        copyBuilder.paused(false);
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
        final var op = context.body().tokenUnpause();
        if (!op.hasToken()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }
    }

    @Override
    public BaseRecordBuilder newRecordBuilder() {
        return new BaseRecordBuilder<>();
    }
}
