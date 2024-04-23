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

import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.LONG_ACCOUNT_AMOUNT_BYTES;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsage.LONG_BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.FeesConfig;
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
        // Todo implement the preHandle logic
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        // Todo: Implement the pureChecks logic
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var op = context.body().tokenRejectOrThrow();
        final var rejections = op.rejections();
        // Todo: Implement the logic for token rejection
    }

    @NonNull
    @Override
    public Fees calculateFees(final FeeContext feeContext) {
        final var body = feeContext.body();
        final var op = body.tokenRejectOrThrow();
        final var config = feeContext.configuration();
        final var tokenMultiplier =
                requireNonNull(config).getConfigData(FeesConfig.class).tokenTransferUsageMultiplier();

        final int totalRejections = op.rejections().size();
        int numOfFungibleTokenRejections = 0;
        int numOfNFTRejections = 0;
        for (final var rejection : op.rejections()) {
            if (rejection.hasFungibleToken()) {
                numOfFungibleTokenRejections++;
            } else {
                numOfNFTRejections++;
            }
        }

        final int weightedTokensInvolved = tokenMultiplier * totalRejections;
        final int weightedFungibleTokens = tokenMultiplier * numOfFungibleTokenRejections;

        final long bpt =
                calculateBytesPerTransaction(weightedTokensInvolved, weightedFungibleTokens, numOfNFTRejections);
        final long rbs = calculateRamByteSeconds(weightedTokensInvolved, weightedFungibleTokens, numOfNFTRejections);

        return feeContext
                .feeCalculator(getSubType(numOfNFTRejections))
                .addBytesPerTransaction(bpt)
                .addRamByteSeconds(rbs * USAGE_PROPERTIES.legacyReceiptStorageSecs())
                .calculate();
    }

    private SubType getSubType(final int numOfNFTRejections) {
        return numOfNFTRejections != 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
    }

    private long calculateBytesPerTransaction(
            final int weightedTokens, final int weightedFungibleTokens, final int numNFTRejections) {
        return weightedTokens * LONG_BASIC_ENTITY_ID_SIZE
                + weightedFungibleTokens * LONG_ACCOUNT_AMOUNT_BYTES
                + TOKEN_ENTITY_SIZES.bytesUsedForUniqueTokenTransfers(numNFTRejections);
    }

    private long calculateRamByteSeconds(
            final int weightedTokensInvolved, final int weightedFungibleTokens, final int numOfNFTRejections) {
        return USAGE_PROPERTIES.legacyReceiptStorageSecs() * TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(
                weightedTokensInvolved, weightedFungibleTokens, numOfNFTRejections);
    }
}
