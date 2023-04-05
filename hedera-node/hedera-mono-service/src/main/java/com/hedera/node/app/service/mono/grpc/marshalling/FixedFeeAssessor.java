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

package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.fees.CustomFeePayerExemptions;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FixedFeeAssessor {
    private final HtsFeeAssessor htsFeeAssessor;
    private final HbarFeeAssessor hbarFeeAssessor;
    private final CustomFeePayerExemptions customFeePayerExemptions;

    @Inject
    public FixedFeeAssessor(
            HtsFeeAssessor htsFeeAssessor,
            HbarFeeAssessor hbarFeeAssessor,
            CustomFeePayerExemptions customFeePayerExemptions) {
        this.htsFeeAssessor = htsFeeAssessor;
        this.hbarFeeAssessor = hbarFeeAssessor;
        this.customFeePayerExemptions = customFeePayerExemptions;
    }

    /**
     * Assess the given fixed fee, which may be either a top-level fixed fee or a fallback royalty
     * fee.
     *
     * @param payer the payer of the fixed fee
     * @param feeMeta the metadata for the token charging the fixed fee
     * @param fee the fixed fee to assess
     * @param changeManager the balance change manager to use for the assessment
     * @param accumulator the accumulator for the assessed custom fees
     * @param isFallbackFee whether the fee is a fallback royalty fee
     * @return OK if the fee was assessed successfully, or a failure code if not
     */
    public ResponseCodeEnum assess(
            final Id payer,
            final CustomFeeMeta feeMeta,
            final FcCustomFee fee,
            final BalanceChangeManager changeManager,
            final List<AssessedCustomFeeWrapper> accumulator,
            final boolean isFallbackFee) {
        if (customFeePayerExemptions.isPayerExempt(feeMeta, fee, payer)) {
            return OK;
        }

        final var fixedSpec = fee.getFixedFeeSpec();
        if (fixedSpec.getTokenDenomination() == null) {
            return hbarFeeAssessor.assess(payer, fee, changeManager, accumulator, isFallbackFee);
        } else {
            return htsFeeAssessor.assess(
                    payer, feeMeta, fee, changeManager, accumulator, isFallbackFee);
        }
    }
}
