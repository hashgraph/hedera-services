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

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#TOKEN_CANCEL_AIRDROP}.
 * This transaction type is used to reject tokens from an account and send them back to the treasury.
 */
public class TokenCancelAirdropHandler extends BaseTokenHandler implements TransactionHandler {

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {}

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {}

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.cancelTokenAirdropEnabled(), NOT_SUPPORTED);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        return Fees.FREE;
    }
}
