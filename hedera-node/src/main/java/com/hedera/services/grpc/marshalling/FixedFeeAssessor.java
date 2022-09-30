/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.fees.CustomFeePayerExemptions;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
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

    public ResponseCodeEnum assess(
            Id payer,
            CustomFeeMeta feeMeta,
            FcCustomFee fee,
            BalanceChangeManager changeManager,
            List<FcAssessedCustomFee> accumulator) {
        if (customFeePayerExemptions.isPayerExempt(feeMeta, fee, payer)) {
            return OK;
        }

        final var fixedSpec = fee.getFixedFeeSpec();
        if (fixedSpec.getTokenDenomination() == null) {
            return hbarFeeAssessor.assess(payer, fee, changeManager, accumulator);
        } else {
            return htsFeeAssessor.assess(payer, feeMeta, fee, changeManager, accumulator);
        }
    }
}
