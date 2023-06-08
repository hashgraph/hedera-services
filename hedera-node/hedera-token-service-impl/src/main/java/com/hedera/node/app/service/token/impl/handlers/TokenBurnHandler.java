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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.service.mono.txns.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_BURN}.
 */
@Singleton
public class TokenBurnHandler implements TransactionHandler {
    @Inject
    public TokenBurnHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenBurnOrThrow();
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMetadata = tokenStore.getTokenMeta(tokenId);
        if (tokenMetadata == null) throw new PreCheckException(INVALID_TOKEN_ID);
        // we will fail in handle() if token has no supply key
        if (tokenMetadata.hasSupplyKey()) {
            context.requireKey(tokenMetadata.supplyKey());
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenBurnOrThrow();
        final var fungibleCount = op.amount();
        final var serialNums = op.serialNumbers();

        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);

        // If a positive fungible amount is present, the NFT serial numbers must be empty
        validateFalsePreCheck(fungibleCount > 0 && !serialNums.isEmpty(), INVALID_TRANSACTION_BODY);

        validateFalsePreCheck(fungibleCount < 0, INVALID_TOKEN_BURN_AMOUNT);

        // Validate the NFT serial numbers
        if (fungibleCount < 1 && !serialNums.isEmpty()) {
            for (final var serialNumber : op.serialNumbers()) {
                validateTruePreCheck(serialNumber > 0, INVALID_NFT_ID);
            }
        }

//        return validateTokenOpsWith(
//                op.getSerialNumbersCount(),
//                op.getAmount(),
//                dynamicProperties.areNftsEnabled(),
//                INVALID_TOKEN_BURN_AMOUNT,
//                op.getSerialNumbersList(),
//                validator::maxBatchSizeBurnCheck);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
