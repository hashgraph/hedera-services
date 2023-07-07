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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FractionalFee;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

import static com.hedera.node.app.service.mono.grpc.marshalling.FeeAssessor.IS_NOT_FALLBACK_FEE;
import static com.hedera.node.app.service.mono.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.safeFractionMultiply;

@Singleton
public class CustomFractionalFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;

    @Inject
    public CustomFractionalFeeAssessor(CustomFixedFeeAssessor fixedFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
    }

    public void assessFractionFees(final CustomFeeMeta feeMeta,
                                   final AccountID sender,
                                   final Map<AccountID, Long> hbarAdjustments,
                                   final Map<TokenID, Map<AccountID, Long>> htsAdjustments) {
       for(final var fee : feeMeta.customFees()){
           final var collector = fee.feeCollectorAccountId();
           // If the collector 0.0.C for a fractional fee is trying to send X units to
           // a receiver 0.0.R, then we want to let all X units go to 0.0.R, instead of
           // reclaiming some fraction of them
           if(fee.fee().kind().equals(CustomFee.FeeOneOfType.FRACTIONAL_FEE) || sender.equals(collector)){
               continue;
           }
           final var fractionalFee = fee.fractionalFee();
           if(fractionalFee.netOfTransfers()){
               final var addedFee = fixedFee(
                       assessedAmount, denom.asEntityId(), fee.getFeeCollector(), fee.getAllCollectorsAreExempt());
               fixedFeeAssessor.assess(payer, feeMeta, addedFee, changeManager, accumulator, IS_NOT_FALLBACK_FEE);
           }
           final var filteredCredits =
       }

    }


        private long amountOwedGiven(long initialUnits, FractionalFee fractionalFee) {
        final var numerator = fractionalFee.fractionalAmount().numerator();
        final var denominator = fractionalFee.fractionalAmount().denominator();

        final var nominalFee = safeFractionMultiply(numerator, denominator, initialUnits);
            long effectiveFee = Math.max(nominalFee, fractionalFee.minimumAmount());
            if (fractionalFee.maximumAmount() > 0) {
                effectiveFee = Math.min(effectiveFee, fractionalFee.maximumAmount());
            }
            return effectiveFee;
        }
}
