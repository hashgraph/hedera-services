/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetStakers;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.CryptoGetStakersQuery;
import com.hederahashgraph.api.proto.java.Query;
import org.junit.jupiter.api.Assertions;

public class VerifyGetStakersNotSupported extends UtilOp {
    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        CryptoGetStakersQuery.Builder op = CryptoGetStakersQuery.newBuilder().setAccountID(asAccount("0.0.2"));
        Query query = Query.newBuilder().setCryptoGetProxyStakers(op).build();
        final var response = spec.targetNetworkOrThrow().send(query, CryptoGetStakers, targetNodeFor(spec));
        Assertions.assertEquals(
                NOT_SUPPORTED, response.getCryptoGetProxyStakers().getHeader().getNodeTransactionPrecheckCode());
        return false;
    }
}
