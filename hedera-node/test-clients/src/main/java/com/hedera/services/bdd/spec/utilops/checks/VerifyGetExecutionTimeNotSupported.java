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

package com.hedera.services.bdd.spec.utilops.checks;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;

public class VerifyGetExecutionTimeNotSupported extends UtilOp {
    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        NetworkGetExecutionTimeQuery.Builder op = NetworkGetExecutionTimeQuery.newBuilder()
                .addAllTransactionIds(List.of(TransactionID.getDefaultInstance()));
        Query query = Query.newBuilder().setNetworkGetExecutionTime(op).build();
        final var response = spec.targetNetworkOrThrow()
                .send(query, HederaFunctionality.NetworkGetExecutionTime, targetNodeFor(spec));
        assertEquals(
                NOT_SUPPORTED, response.getNetworkGetExecutionTime().getHeader().getNodeTransactionPrecheckCode());
        return false;
    }
}
