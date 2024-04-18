/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.TokenRejectRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#TOKEN_REJECT}.
 */
@Singleton
public class TokenRejectHandler extends BaseTokenHandler implements TransactionHandler {

    @Inject
    public TokenRejectHandler() {}

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        // Todo implement the preHandle logic
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenRejectOrThrow();
        final var rejections = op.rejections();
        validateTruePreCheck(!rejections.isEmpty(), INVALID_TRANSACTION_BODY);
        // Todo: Implement the pureChecks logic

    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var op = context.body().tokenRejectOrThrow();
        final var rejections = op.rejections();
        final var recordBuilder = context.recordBuilder(TokenRejectRecordBuilder.class);
        final var payer = context.payer();
        // Todo: Implement the logic for token rejection
    }

    @NonNull
    @Override
    public Fees calculateFees(final FeeContext feeContext) {
        // Todo: Implement the logic for calculating fees
        return Fees.FREE;
    }
}
