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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.asFixedFee;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.getFungibleCredits;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.spi.workflows.HandleContext;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class CustomRoyaltyFeeAssessor {
    private HandleContext handleContext;
    private CustomFixedFeeAssessor fixedFeeAssessor;

    public CustomRoyaltyFeeAssessor(
            final CustomFixedFeeAssessor fixedFeeAssessor, final TransferContextImpl transferContext) {
        this.handleContext = transferContext.getHandleContext();
        this.fixedFeeAssessor = fixedFeeAssessor;
    }

    public void assessRoyaltyFees(
            final CustomFeeMeta feeMeta,
            final AccountID sender,
            final AccountID receiver,
            final Map<AccountID, Long> hbarAdjustments,
            final Map<TokenID, Map<AccountID, Long>> htsAdjustments,
            final Set<TokenID> exemptDebits,
            final Set<Pair<AccountID, TokenID>> royaltiesPaid) {
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final var tokenId = feeMeta.tokenId();
        if (royaltiesPaid.contains(Pair.of(sender, tokenId))) {
            return;
        }
        final var exchangedValue = getFungibleCredits(htsAdjustments.get(tokenId), hbarAdjustments, sender);
        for (final var fee : feeMeta.customFees()) {
            final var collector = fee.feeCollectorAccountId();
            if (!fee.fee().kind().equals(CustomFee.FeeOneOfType.ROYALTY_FEE)) {
                continue;
            }
            final var royaltyFee = fee.royaltyFeeOrThrow();
            if (exchangedValue.isEmpty()) {
                if (!royaltyFee.hasFallbackFee()) {
                    return;
                }
                final var fallback = royaltyFee.fallbackFee();
                // A NFT transfer with royalty fees to an unknown alias is not possible, since
                // the auto-created
                // account will not have any hbar to pay the fallback fee
                validateTrue(
                        accountStore.get(receiver).tinybarBalance() != 0,
                        INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
                final var fallbackFee = asFixedFee(
                        fallback.amount(), fallback.denominatingTokenId(), collector, fee.allCollectorsAreExempt());
                fixedFeeAssessor.assessFixedFee(
                        feeMeta, receiver, fallbackFee, hbarAdjustments, htsAdjustments, exemptDebits);
            }
        }
    }
}
