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

import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.FEE_MATRICES_CONST;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;

public final class FixedUsageEstimates {
    private static final int BYTES_PER_SEMANTIC_VERSION = 12;

    private FixedUsageEstimates() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static final FeeComponents ZERO_USAGE = FeeComponents.getDefaultInstance();

    static final FeeComponents GET_VERSION_INFO_NODE_USAGE =
            FeeComponents.newBuilder()
                    .setConstant(FEE_MATRICES_CONST)
                    .setBpt(BASIC_QUERY_HEADER)
                    .setBpr(BASIC_QUERY_RES_HEADER + 2 * BYTES_PER_SEMANTIC_VERSION)
                    .build();

    public static FeeData getVersionInfoUsage() {
        return FeeData.newBuilder()
                .setNetworkdata(ZERO_USAGE)
                .setServicedata(ZERO_USAGE)
                .setNodedata(GET_VERSION_INFO_NODE_USAGE)
                .build();
    }
}
