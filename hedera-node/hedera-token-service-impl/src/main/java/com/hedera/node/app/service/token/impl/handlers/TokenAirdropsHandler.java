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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropsHandler implements TransactionHandler {

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropsHandler() {}

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        // todo
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        // todo
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenAirdrop();
        final var topLevelPayer = context.payer();

        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        // todo add validations
        // validator.validateSemantics(op, ledgerConfig, hederaConfig, tokensConfig);

        // todo add handle logic
        //        final var recordBuilder = context.recordBuilder(TokenAirdropRecordBuilder.class);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        // todo calculate fees
        return Fees.FREE;
    }
}
