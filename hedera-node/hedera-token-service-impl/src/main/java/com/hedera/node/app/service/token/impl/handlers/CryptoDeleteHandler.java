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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.hapi.utils.fee.CryptoFeeBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.api.TokenServiceApi.FreeAliasOnDeletion;
import com.hedera.node.app.service.token.records.CryptoDeleteStreamBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.AccountsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_DELETE}.
 */
@Singleton
public class CryptoDeleteHandler implements TransactionHandler {
    private final CryptoFeeBuilder usageEstimator = new CryptoFeeBuilder();

    /**
     * Default constructor for injection.
     */
    @Inject
    public CryptoDeleteHandler() {
        // Exists for injection
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.cryptoDeleteOrThrow();

        if (!op.hasDeleteAccountID() || !op.hasTransferAccountID()) {
            throw new PreCheckException(ACCOUNT_ID_DOES_NOT_EXIST);
        }

        if (op.deleteAccountIDOrThrow().equals(op.transferAccountIDOrThrow())) {
            throw new PreCheckException(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());
        final var op = context.body().cryptoDeleteOrThrow();
        final var deleteAccountId = op.deleteAccountIDOrElse(AccountID.DEFAULT);
        final var transferAccountId = op.transferAccountIDOrElse(AccountID.DEFAULT);
        context.requireKeyOrThrow(deleteAccountId, INVALID_ACCOUNT_ID)
                .requireKeyIfReceiverSigRequired(transferAccountId, INVALID_TRANSFER_ACCOUNT_ID);
    }

    @Override
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);
        final var accountsConfig = context.configuration().getConfigData(AccountsConfig.class);
        final var op = context.body().cryptoDeleteOrThrow();
        context.storeFactory()
                .serviceApi(TokenServiceApi.class)
                .deleteAndTransfer(
                        op.deleteAccountIDOrThrow(),
                        op.transferAccountIDOrThrow(),
                        context.expiryValidator(),
                        context.savepointStack().getBaseBuilder(CryptoDeleteStreamBuilder.class),
                        accountsConfig.releaseAliasAfterDeletion() ? FreeAliasOnDeletion.YES : FreeAliasOnDeletion.NO);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var body = feeContext.body();
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(sigValueObj ->
                        usageEstimator.getCryptoDeleteTxFeeMatrices(CommonPbjConverters.fromPbj(body), sigValueObj));
    }
}
