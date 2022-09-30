/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;

/** Represents logic that decides if a collector is exempt from a given custom fee */
public class CollectorExemptions implements CustomFeeExemptions {
    @Override
    public boolean isPayerExempt(CustomFeeMeta feeMeta, FcCustomFee fee, Id payer) {
        var collectorIds =
                feeMeta.customFees().stream().map(FcCustomFee::getFeeCollectorAsId).toList();
        return collectorIds.contains(payer) && fee.getAllCollectorsAreExempt();
    }
}
