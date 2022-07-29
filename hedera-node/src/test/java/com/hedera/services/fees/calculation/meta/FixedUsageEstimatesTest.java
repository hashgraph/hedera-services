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
package com.hedera.services.fees.calculation.meta;

import static com.hedera.services.fees.calculation.meta.FixedUsageEstimates.GET_VERSION_INFO_NODE_USAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.FeeComponents;
import org.junit.jupiter.api.Test;

class FixedUsageEstimatesTest {
    @Test
    void getVersionInfoUsageWorks() {
        final var feeData = FixedUsageEstimates.getVersionInfoUsage();

        assertEquals(FeeComponents.getDefaultInstance(), feeData.getNetworkdata());
        assertEquals(FeeComponents.getDefaultInstance(), feeData.getServicedata());
        assertEquals(GET_VERSION_INFO_NODE_USAGE, feeData.getNodedata());
    }
}
