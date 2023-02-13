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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenFeeScheduleUpdate}.
 */
@Singleton
public class TokenFeeScheduleUpdateHandler implements TransactionHandler {
    @Inject
    public TokenFeeScheduleUpdateHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PrehandleHandlerContext} which collects all information that will
     *     be passed to {@link #handle(TransactionMetadata)}
     * @param tokenStore the {@link ReadableTokenStore} to use to resolve token metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final PrehandleHandlerContext context,
            @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(context);
        final var op = context.getTxn().getTokenFeeScheduleUpdate();
        final var tokenId = op.getTokenId();
        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        if (tokenMeta.failed()) {
            context.status(tokenMeta.failureReason());
        } else {
            final var tokenMetadata = tokenMeta.metadata();
            final var feeScheduleKey = tokenMetadata.feeScheduleKey();
            if (feeScheduleKey.isPresent()) {
                context.addToReqNonPayerKeys(feeScheduleKey.get());
                for (final var customFee : op.getCustomFeesList()) {
                    final var collector = customFee.getFeeCollectorAccountId();
                    context.addNonPayerKeyIfReceiverSigRequired(
                            collector, INVALID_CUSTOM_FEE_COLLECTOR);
                }
            }
            // we do not set a failure status if a fee schedule key is not present for the token,
            // we choose to fail with TOKEN_HAS_NO_FEE_SCHEDULE_KEY in the handle() method
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }
}
