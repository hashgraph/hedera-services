/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Assesses custom fees for a given crypto transfer transaction.
 */
@Singleton
public class CustomFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;
    private final CustomFractionalFeeAssessor fractionalFeeAssessor;
    private final CustomRoyaltyFeeAssessor royaltyFeeAssessor;
    private int initialNftChanges = 0;

    @Inject
    public CustomFeeAssessor(
            @NonNull final CustomFixedFeeAssessor fixedFeeAssessor,
            @NonNull final CustomFractionalFeeAssessor fractionalFeeAssessor,
            @NonNull final CustomRoyaltyFeeAssessor royaltyFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.fractionalFeeAssessor = fractionalFeeAssessor;
        this.royaltyFeeAssessor = royaltyFeeAssessor;
    }

    private int numNftTransfers(final CryptoTransferTransactionBody op) {
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        var nftTransfers = 0;
        for (final var xfer : tokenTransfers) {
            nftTransfers += xfer.nftTransfersOrElse(emptyList()).size();
        }
        return nftTransfers;
    }

    public void assess(
            final AccountID sender,
            final CustomFeeMeta feeMeta,
            final int maxTransfersSize,
            final AccountID receiver,
            final AssessmentResult result,
            final HandleContext ctx) {
        fixedFeeAssessor.assessFixedFees(feeMeta, sender, result);
        validateBalanceChanges(result, maxTransfersSize);

        // A FUNGIBLE_COMMON token can have fractional fees but not royalty fees.
        // A NON_FUNGIBLE_UNIQUE token can have royalty fees but not fractional fees.
        // So check token type and do further assessment
        if (feeMeta.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
            fractionalFeeAssessor.assessFractionalFees(feeMeta, sender, result);
        } else {
            royaltyFeeAssessor.assessRoyaltyFees(feeMeta, sender, receiver, result, ctx);
        }
        validateBalanceChanges(result, maxTransfersSize);
    }

    private void validateBalanceChanges(final AssessmentResult result, final int maxTransfersSize) {
        var inputFungibleTransfers = 0;
        var newFungibleTransfers = 0;
        for (final var entry : result.getMutableInputTokenAdjustments().entrySet()) {
            inputFungibleTransfers += entry.getValue().size();
        }
        for (final var entry : result.getHtsAdjustments().entrySet()) {
            newFungibleTransfers += entry.getValue().size();
        }
        final var balanceChanges = result.getHbarAdjustments().size()
                + newFungibleTransfers
                + result.getInputHbarAdjustments().size()
                + inputFungibleTransfers
                + initialNftChanges;
        validateFalse(balanceChanges > maxTransfersSize, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
    }

    /**
     * Sets the initial NFT changes for the transaction. These are not going to change in the course of
     * assessing custom fees.
     * @param op the transaction body
     */
    public void calculateAndSetInitialNftChanges(final CryptoTransferTransactionBody op) {
        initialNftChanges = numNftTransfers(op);
    }

    public void resetInitialNftChanges() {
        initialNftChanges = 0;
    }
}
