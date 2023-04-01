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

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static java.util.Objects.requireNonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_UPDATE}.
 *
 * <p><b>NOTE:</b> this class intentionally changes the following error response codes relative to
 * SigRequirements:
 *
 * <ol>
 *   <li>When a missing account is used as a token treasury, fails with {@code INVALID_ACCOUNT_ID}
 *       rather than {@code ACCOUNT_ID_DOES_NOT_EXIST}.
 * </ol>
 *
 * * EET expectations may need to be updated accordingly
 */
@Singleton
public class TokenUpdateHandler implements TransactionHandler {
    @Inject
    public TokenUpdateHandler() {
        // Exists for injection
    }

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_UPDATE}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@link #handle(TokenUpdateTransactionBody)}
     * @param tokenStore the {@link ReadableTokenStore} to use to resolve token metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(context);
        final var op = context.getTxn().tokenUpdateOrThrow();
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);

        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        if (tokenMeta.failed()) {
            context.status(tokenMeta.failureReason());
            return;
        }
        final var tokenMetadata = tokenMeta.metadata();
        final var adminKey = tokenMetadata.adminKey();
        adminKey.ifPresent(context::addToReqNonPayerKeys);
        if (op.hasAutoRenewAccount()) {
            context.addNonPayerKey(op.autoRenewAccountOrThrow(), INVALID_AUTORENEW_ACCOUNT);
        }
        if (op.hasTreasury()) {
            context.addNonPayerKey(op.treasuryOrThrow());
        }
        if (op.hasAdminKey()) {
            final var newAdminKey = asHederaKey(op.adminKeyOrThrow());
            newAdminKey.ifPresent(context::addToReqNonPayerKeys);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param tx the transaction to handle
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TokenUpdateTransactionBody tx) {
        requireNonNull(tx);
        throw new UnsupportedOperationException("Not implemented");
    }
}
