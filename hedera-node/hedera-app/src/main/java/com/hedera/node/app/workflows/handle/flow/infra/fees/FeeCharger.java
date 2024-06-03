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

package com.hedera.node.app.workflows.handle.flow.infra.fees;

import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;

public interface FeeCharger {
    void chargeFees(ValidationResult validationResult, final NodeInfo creator, Fees fees);

    default boolean chargeNetworkFees(long networkFee) {
        return true;
    }
}
