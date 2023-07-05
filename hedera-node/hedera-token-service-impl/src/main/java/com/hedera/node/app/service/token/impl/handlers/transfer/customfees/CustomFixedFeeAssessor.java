/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.grpc.marshalling.AdjustmentUtils;
import com.hedera.node.app.service.mono.grpc.marshalling.AssessedCustomFeeWrapper;
import com.hedera.node.app.service.mono.grpc.marshalling.CustomFeeMeta;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class CustomFixedFeeAssessor {

    @Inject
    public CustomFixedFeeAssessor(){
    }

    /**
     * Assess the given fixed fee, which may be either a top-level fixed fee or a fallback royalty
     * fee.
     *
     * @param payer the payer of the fixed fee
     * @param feeMeta the metadata for the token charging the fixed fee
     * @param fee the fixed fee to assess
     * @param isFallbackFee whether the fee is a fallback royalty fee
     * @return OK if the fee was assessed successfully, or a failure code if not
     */
    public TransactionBody.Builder assess(
            final AccountID payer,
            final CustomFeeMeta feeMeta,
            final CustomFee fee,
            final boolean isFallbackFee) {
        if (isPayerExempt(feeMeta, fee, payer)) {
            return;
        }

        final var fixedSpec = fee.fixedFeeOrThrow();
        if (fixedSpec.hasDenominatingTokenId()) {
            return assessHbarFees(payer, fee, changeManager, accumulator, isFallbackFee);
        } else {
            return assessHtsFees(payer, feeMeta, fee, changeManager, accumulator, isFallbackFee);
        }
    }

    public ResponseCodeEnum assessHbarFees(
            final AccountID payer,
            final FcCustomFee hbarFee,
            boolean isFallbackFee) {
        final var collector = hbarFee.getFeeCollectorAsId();
        final var fixedSpec = hbarFee.getFixedFeeSpec();
        final var amount = fixedSpec.getUnitsToCollect();
        adjustForAssessedHbar(payer, collector, amount, changeManager, isFallbackFee);
        final var effPayerAccountNums = new AccountID[] {payer.asGrpcAccount()};
        final var assessed = new AssessedCustomFeeWrapper(collector.asEntityId(), amount, effPayerAccountNums);
        accumulator.add(assessed);
        return OK;
    }

    public ResponseCodeEnum assessHtsFees(
            AccountID payer,
            CustomFeeMeta chargingTokenMeta,
            CustomFee htsFee,
            boolean isFallbackFee) {
        final var collector = htsFee.feeCollectorAccountId();
        final var fixedSpec = htsFee.fixedFee();
        final var amount = fixedSpec.amount();
        final var denominatingToken = fixedSpec.denominatingTokenId();
        AdjustmentUtils.adjustForAssessed(
                payer, chargingTokenMeta.tokenId(), collector, denominatingToken, amount, changeManager, isFallbackFee);

        final var effPayerAccountNums = new AccountID[] {payer.asGrpcAccount()};
        final var assessed = new AssessedCustomFeeWrapper(
                htsFee.feeCollectorAccountId(), fixedSpec.denominatingTokenId(), amount, effPayerAccountNums);
        accumulator.add(assessed);

        return OK;
    }

}
